package eu.europa.ec.edelivery.smp.services.ui;

import eu.europa.ec.edelivery.security.cert.CertificateValidator;
import eu.europa.ec.edelivery.security.utils.X509CertificateUtils;
import eu.europa.ec.edelivery.smp.data.dao.UserDao;
import eu.europa.ec.edelivery.smp.data.model.DBUser;
import eu.europa.ec.edelivery.smp.data.ui.CertificateRO;
import eu.europa.ec.edelivery.smp.exceptions.CertificateNotTrustedException;
import eu.europa.ec.edelivery.smp.logging.SMPLogger;
import eu.europa.ec.edelivery.smp.logging.SMPLoggerFactory;
import eu.europa.ec.edelivery.smp.services.CRLVerifierService;
import eu.europa.ec.edelivery.smp.services.ConfigurationService;
import eu.europa.ec.edelivery.text.DistinguishedNamesCodingUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.BasicAttribute;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;
import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static eu.europa.ec.edelivery.smp.logging.SMPMessageCode.SEC_TRUSTSTORE_CERT_INVALID;
import static eu.europa.ec.edelivery.smp.logging.SMPMessageCode.SEC_USER_CERT_INVALID;
import static java.util.Collections.list;
import static java.util.Locale.US;

/**
 * @author Joze Rihtarsic
 * @since 4.1
 */
@Service
public class UITruststoreService {

    private static final SMPLogger LOG = SMPLoggerFactory.getLogger(UITruststoreService.class);

    private static final String CERT_ERROR_MSG_NOT_TRUSTED="Certificate is not trusted!";
    private static final String CERT_ERROR_MSG_REVOKED= "Certificate is revoked!";
    private static final String CERT_ERROR_MSG_EXPIRED= "Certificate is expired!";
    private static final String CERT_ERROR_MSG_NOT_YET_VALID= "Certificate is not yet valid!";
    private static final String CERT_ERROR_MSG_NOT_VALIDATED= "Certificate not validated!";

    private static final ThreadLocal<DateFormat> dateFormatLocal = ThreadLocal.withInitial(() ->
            new SimpleDateFormat("MMM d hh:mm:ss yyyy zzz", US)
    );

    // dependent beans
    private final ConfigurationService configurationService;
    private final CRLVerifierService crlVerifierService;
    private final ConversionService conversionService;
    private final UserDao userDao;

    List<String> normalizedTrustedList = new ArrayList<>();
    Map<String, X509Certificate> truststoreCertificates = new HashMap();
    List<CertificateRO> certificateROList = new ArrayList<>();
    long lastUpdateTrustStoreFileTime = 0;
    File lastUpdateTrustStoreFile = null;
    TrustManager[] trustManagers;
    KeyStore trustStore = null;

    public UITruststoreService(ConfigurationService configurationService, CRLVerifierService crlVerifierService, ConversionService conversionService, UserDao userDao) {
        this.configurationService = configurationService;
        this.crlVerifierService = crlVerifierService;
        this.conversionService = conversionService;
        this.userDao = userDao;
    }

    @PostConstruct
    public void init() {
        setupJCEProvider();
        refreshData();
    }

    private void setupJCEProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null ) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public boolean truststoreNotConfigured() {
        File truststoreFile = configurationService.getTruststoreFile();
        return truststoreFile == null;
    }


    /**
     * Method  validates the configuration properties and refresh the
     * cached data
     */
    public void refreshData() {
        if (truststoreNotConfigured()) {
            LOG.warn("Truststore filename is not set! Certificates will not be validated by trusted issuers!");
            return;
        }

        // load keystore
        File truststoreFile = getTruststoreFile();
        trustStore = loadTruststore(truststoreFile);
        if (trustStore == null) {
            LOG.error("Keystore: [{}] is not loaded! Check the truststore filename" +
                    " and the configuration!", truststoreFile.getAbsolutePath());
            return;
        }
        // init key managers for TLS
        TrustManager[] trustManagersTemp;
        try {
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagersTemp = tmf.getTrustManagers();
        } catch (KeyStoreException | NoSuchAlgorithmException exception) {
            LOG.error("Error occurred while initialize trustManagers : "
                    + truststoreFile.getAbsolutePath() + " Error: " + ExceptionUtils.getRootCauseMessage(exception), exception);
            return;
        }


        // load keys for signature
        List<String> tmpList = new ArrayList<>();
        Map<String, X509Certificate> hmCertificates = new HashMap<>();

        try {
            List<String> aliases = list(trustStore.aliases());
            for (String alias : aliases) {
                Certificate cert = trustStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    X509Certificate x509Certificate = (X509Certificate) cert;
                    String subject = x509Certificate.getSubjectX500Principal().getName();

                    subject = DistinguishedNamesCodingUtil.normalizeDN(subject,
                            DistinguishedNamesCodingUtil.getCommonAttributesDN());
                    tmpList.add(subject);
                    hmCertificates.put(alias, x509Certificate);
                    validateAndLogError(x509Certificate, alias);
                }
            }
        } catch (Exception exception) {
            LOG.error("Could not load truststore certificates Error: " + ExceptionUtils.getRootCauseMessage(exception), exception);
            return;
        }
        truststoreCertificates.clear();
        normalizedTrustedList.clear();

        trustManagers = trustManagersTemp;
        normalizedTrustedList.addAll(tmpList);
        truststoreCertificates.putAll(hmCertificates);

        lastUpdateTrustStoreFileTime = truststoreFile.lastModified();
        lastUpdateTrustStoreFile = truststoreFile;
        // clear list to reload RO when required
        certificateROList.clear();
    }

    protected void validateAndLogError(X509Certificate x509Certificate, String alias) {
        try {
            x509Certificate.checkValidity();
        } catch (CertificateExpiredException | CertificateNotYetValidException ex) {
            LOG.securityWarn(SEC_TRUSTSTORE_CERT_INVALID, alias, ExceptionUtils.getRootCauseMessage(ex));
        }
    }

    public CertificateRO getCertificateData(byte[] buff) {
        return getCertificateData(buff, false);
    }

    /**
     * Validate certificate!
     *
     * @param buff     - bytearray of the certificate (pem of or der)
     * @param validate
     * @return
     */
    public CertificateRO getCertificateData(byte[] buff, boolean validate) {
        X509Certificate cert;
        CertificateRO cro;
        try {
            cert = X509CertificateUtils.getX509Certificate(buff);
        } catch (CertificateException e) {
            LOG.debug("Error occurred while parsing the certificate ", e);
            LOG.warn("Can not parse the certificate with error:[{}]!", ExceptionUtils.getRootCauseMessage(e));
            cro = new CertificateRO();
            cro.setInvalid(true);
            cro.setInvalidReason("Can not read the certificate!");
            return cro;
        }

        cro = convertToRo(cert);
        if (validate) {
            validateCertificate(cert, cro);
        }
        return cro;
    }

    public void validateCertificate(X509Certificate cert, CertificateRO cro) {
        // first expect the worst
        cro.setInvalid(true);
        cro.setInvalidReason(CERT_ERROR_MSG_NOT_VALIDATED);
        try {
            checkFullCertificateValidity(cert);
            validateCertificateNotUsed(cro);
            cro.setInvalid(false);
            cro.setInvalidReason(null);
        } catch (CertificateExpiredException ex) {
            LOG.securityError(SEC_USER_CERT_INVALID, cro.getCertificateId(), ex.getMessage());
            cro.setInvalidReason(CERT_ERROR_MSG_EXPIRED);
        } catch (CertificateNotYetValidException ex) {
            LOG.securityError(SEC_USER_CERT_INVALID, cro.getCertificateId(), ex.getMessage());
            cro.setInvalidReason(CERT_ERROR_MSG_NOT_YET_VALID);
        } catch (CertificateRevokedException ex) {
            LOG.securityError(SEC_USER_CERT_INVALID, cro.getCertificateId(), ex.getMessage());
            cro.setInvalidReason(CERT_ERROR_MSG_REVOKED);
        } catch (CertificateNotTrustedException ex) {
            LOG.securityError(SEC_USER_CERT_INVALID, cro.getCertificateId(), ex.getMessage());
            cro.setInvalidReason(CERT_ERROR_MSG_NOT_TRUSTED);
        } catch (CertificateException e) {
            LOG.securityError(SEC_USER_CERT_INVALID, e, cro.getCertificateId(), e.getMessage());
            if (ExceptionUtils.getRootCause(e) instanceof CertPathValidatorException) {
                cro.setInvalidReason("Certificate is not trusted! Invalid certificate policy path!");
            } else {
                cro.setInvalidReason(e.getMessage());
            }
        }
    }

    public void validateCertificateWithTruststore(X509Certificate x509Certificate) throws CertificateException {

          if (x509Certificate == null) {
            throw new CertificateException("The X509Certificate is null (Is the client cert header enabled?)! Skip trust validation against the truststore!");
        }
        KeyStore truststore = getTrustStore();
        if (truststore == null) {
            LOG.warn("Truststore is not configured! Skip trust validation against the truststore!");
            return;
        }

        Pattern subjectRegExp = configurationService.getCertificateSubjectRegularExpression();
        List<String> allowedCertificatePolicies = configurationService.getAllowedCertificatePolicies();
        CertificateValidator certificateValidator = new CertificateValidator(
                Collections.emptyList(), truststore,
                subjectRegExp != null ? subjectRegExp.pattern() : null,
                allowedCertificatePolicies != null ? allowedCertificatePolicies : Collections.emptyList());
        LOG.debug("Validate certificate with truststore, subject regexp [{}] and allowed certificate policies [{}]", subjectRegExp, allowedCertificatePolicies);
        certificateValidator.validateCertificate(x509Certificate);
    }

    /**
     * Method validates if certificate public key algorithm is allowed. If the allowedCertificateKeyType list is null or empty, then
     * then all certificate types are allowed.
     *
     * @param x509Certificate certificate to validate
     * @throws CertificateException
     */
    public void validateAllowedCertificateKeyTypes(X509Certificate x509Certificate) throws CertificateException {
        List<String> allowedCertificateKeyTypes = configurationService.getAllowedCertificateKeyTypes();
        if (allowedCertificateKeyTypes == null
                || allowedCertificateKeyTypes.isEmpty()) {
            LOG.debug("No certificate key types configured. Skip certificate key validation.");
            return;
        }

        PublicKey certKey = x509Certificate.getPublicKey();
        if (!StringUtils.equalsAnyIgnoreCase(certKey.getAlgorithm(), allowedCertificateKeyTypes.toArray(new String[]{}))) {
            throw new CertificateException("Certificate does not have allowed key algorithm type! Key type ["
                    + certKey.getAlgorithm() + "] Allowed values ["
                    + allowedCertificateKeyTypes + "]!");
        }
    }

    public void checkFullCertificateValidity(X509Certificate cert) throws CertificateException {
        // test if certificate is valid
        cert.checkValidity();

        // check if certificate or its issuer is on trusted list
        // check only issuer because using Client-cert header we do not have whole chain.
        // if the truststore is empty then truststore validation is ignored
        // backward compatibility
        if (!normalizedTrustedList.isEmpty() && !(isSubjectOnTrustedList(cert.getSubjectX500Principal().getName())
                || isSubjectOnTrustedList(cert.getIssuerDN().getName()))) {

            throw new CertificateNotTrustedException(CERT_ERROR_MSG_NOT_TRUSTED);
        }

        // validate if certificate key type is valid
        validateAllowedCertificateKeyTypes(cert);

        if (trustStore != null) {
            validateCertificateWithTruststore(cert);
        } else {
            LOG.warn("Use legacy certificate validation without truststore. Please configure truststore to increase security");
            validateCertificatePolicyMatchLegacy(cert);
            validateCertificateSubjectExpressionLegacy(cert);
        }

        // check CRL - it is using only HTTP or https
        crlVerifierService.verifyCertificateCRLs(cert);
    }

    public void validateCertificateNotUsed(CertificateRO cert) throws CertificateException {
        Optional<DBUser> user = userDao.findUserByCertificateId(cert.getCertificateId());
        if (user.isPresent()) {
            String msg = "Certificate: [" + cert.getCertificateId() + "] is already used!";
            LOG.debug("Certificate with id: [{}] is already used by user with username [{}]",  cert.getCertificateId() , user.get().getUsername());
            throw new CertificateException(msg);
        }

    }

    public void checkFullCertificateValidity(CertificateRO cert) throws CertificateException {
        // trust data in database

        Date currentDate = Calendar.getInstance().getTime();
        if (cert.getValidFrom() != null && currentDate.before(cert.getValidFrom())) {
            throw new CertificateNotYetValidException("Certificate: " + cert.getCertificateId() + " is valid from: "
                    + dateFormatLocal.get().format(cert.getValidFrom()) + ".");

        }
        if (cert.getValidTo() != null && currentDate.after(cert.getValidTo())) {
            throw new CertificateExpiredException("Certificate: " + cert.getCertificateId() + " was valid to: "
                    + dateFormatLocal.get().format(cert.getValidTo()) + ".");
        }
        // if trusted list is not empty and exists issuer or subject then validate
        if (!normalizedTrustedList.isEmpty() && (
                !StringUtils.isBlank(cert.getIssuer()) || !StringUtils.isBlank(cert.getSubject()))) {

            if (!isSubjectOnTrustedList(cert.getIssuer()) && !isSubjectOnTrustedList(cert.getSubject())) {
                throw new CertificateNotTrustedException(CERT_ERROR_MSG_NOT_TRUSTED);
            }

        }

        // Check crl list
        String url = cert.getCrlUrl();
        if (!StringUtils.isBlank(url) && !StringUtils.isBlank(cert.getSerialNumber())) {
            try {
                crlVerifierService.verifyCertificateCRLs(cert.getSerialNumber(), url);
            } catch (CertificateRevokedException ex) {
                String msg = "Certificate: '" + cert.getCertificateId() + "'" +
                        " is revoked!";
                LOG.securityWarn(SEC_USER_CERT_INVALID, cert.getCertificateId(), msg, ex);
                throw new CertificateException(msg);
            } catch (Throwable th) {
                String msg = "Error occurred while validating CRL for certificate!";
                LOG.error(SMPLogger.SECURITY_MARKER, msg + "Err: " + ExceptionUtils.getRootCauseMessage(th), th);
                throw new CertificateException(msg);
            }
        }
    }

    boolean isTruststoreChanged() {
        File file = getTruststoreFile();
        return !Objects.equals(lastUpdateTrustStoreFile, file) ||
                file != null && file.lastModified() != lastUpdateTrustStoreFileTime;
    }

    public File getTruststoreFile() {
        return configurationService.getTruststoreFile();
    }


    public TrustManager[] getTrustManagers() {
        // check if keystore is changes
        if (isTruststoreChanged()) {
            refreshData();
        }
        return trustManagers;
    }


    protected KeyStore loadTruststore(File truststoreFile) {

        if (truststoreFile == null) {
            LOG.error("Truststore file is not configured! Update SMP configuration!");
            return null;
        }
        // Load the KeyStore.
        if (!truststoreFile.exists()) {
            LOG.error("Truststore file '{}' does not exists!", truststoreFile.getAbsolutePath());
            return null;
        }
        String token = configurationService.getTruststoreCredentialToken();
        if (StringUtils.isEmpty(token)) {
            LOG.error("Truststore credentials are missing in configuration table for truststore: '{}' !", truststoreFile.getName());
            return null;
        }

        try (InputStream truststoreInputStream = new FileInputStream(truststoreFile)) {
            String type = StringUtils.defaultIfEmpty(configurationService.getTruststoreType(), "JKS");
            LOG.info("Load truststore [{}] with type [{}].", truststoreFile, type);
            KeyStore loadedTrustStore = KeyStore.getInstance(type);
            loadedTrustStore.load(truststoreInputStream, token.toCharArray());
            return loadedTrustStore;
        } catch (Exception exception) {
            LOG.error("Could not load truststore:" + truststoreFile + " Error: " + ExceptionUtils.getRootCauseMessage(exception), exception);
        }
        return null;
    }


    public boolean isSubjectOnTrustedList(String subject) {

        // do not validate if list is empty
        if (truststoreNotConfigured() || normalizedTrustedList.isEmpty()) {
            return true;
        }

        if (StringUtils.isBlank(subject)) {
            LOG.warn("Null or empty subject!");
            return false;
        }
        String normSubj = DistinguishedNamesCodingUtil.normalizeDN(subject,
                DistinguishedNamesCodingUtil.getCommonAttributesDN());
        if (isTruststoreChanged()) {
            refreshData();
        }
        return normalizedTrustedList.contains(normSubj);
    }

    public List<String> getNormalizedTrustedList() {
        return normalizedTrustedList;
    }

    /**
     * Delete keys smp keystore
     *
     * @param alias
     */
    public void deleteCertificate(String alias) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException {

        KeyStore keyStore = loadTruststore(getTruststoreFile());
        if (keyStore != null) {
            keyStore.deleteEntry(alias);
            // store keystore
            storeTruststore(keyStore);
            refreshData();
        }
    }

    public String addCertificate(String alias, X509Certificate certificate) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException {

        KeyStore truststore = loadTruststore(getTruststoreFile());
        if (truststore != null) {
            String aliasPrivate = StringUtils.isBlank(alias) ? createAliasFromCert(certificate, truststore) : alias.trim();

            if (truststore.containsAlias(aliasPrivate)) {
                int i = 1;
                while (truststore.containsAlias(aliasPrivate + "_" + i)) {
                    i++;
                }
                aliasPrivate = aliasPrivate + "_" + i;
            }

            truststore.setCertificateEntry(aliasPrivate, certificate);
            // store truststore
            storeTruststore(truststore);
            refreshData();
            return aliasPrivate;
        }
        return null;
    }

    public KeyStore getTrustStore() {
        return trustStore;
    }

    public String createAliasFromCert(X509Certificate x509cert, KeyStore truststore) {


        String dn = x509cert.getSubjectX500Principal().getName();
        String alias = null;
        try {

            LdapName ldapDN = new LdapName(dn);
            Rdn cn = null;
            for (Rdn rdn : ldapDN.getRdns()) {

                if (rdn.size() > 1) {
                    NamingEnumeration enr = rdn.toAttributes().getAll();
                    while (enr.hasMore()) {
                        Object mvRDn = enr.next();
                        if (mvRDn instanceof BasicAttribute) {
                            BasicAttribute ba = (BasicAttribute) mvRDn;
                            if (Objects.equals("CN", ba.getID())) {
                                cn = new Rdn(ba.getID(), ba.get());
                                break;
                            }
                        }
                    }

                } else if (Objects.equals("CN", rdn.getType())) {
                    alias = rdn.getValue().toString().trim();
                    break;
                }
                if (cn != null) {
                    alias = cn.getValue().toString().trim();
                    break;
                }
            }

        } catch (NamingException e) {
            LOG.error("Can not parse certificate subject: " + dn);
        }
        alias = StringUtils.isEmpty(alias) ? UUID.randomUUID().toString() : alias;

        try {
            if (truststore != null && truststore.containsAlias(alias)) {
                int iVal = 1;
                while (truststore.containsAlias(alias + "_" + iVal)) {
                    iVal++;
                }
                alias = alias + "_" + iVal;
            }
        } catch (KeyStoreException e) {
            LOG.error("Error occurred while reading truststore for validating existance of the alias: " + alias, e);
        }
        return alias;
    }


    /**
     * Store keystore
     *
     * @param keyStore to store
     * @throws IOException
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws KeyStoreException
     */
    private void storeTruststore(KeyStore keyStore) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        File keystoreFilePath = getTruststoreFile();
        String token = configurationService.getTruststoreCredentialToken();
        try (FileOutputStream fos = new FileOutputStream(keystoreFilePath)) {
            keyStore.store(fos, token.toCharArray());
        }
    }

    public List<CertificateRO> getCertificateROEntriesList() {

        if (isTruststoreChanged()) {
            refreshData();
            // refresh also the list
            certificateROList.clear();
        }
        if (certificateROList.isEmpty() && !truststoreCertificates.isEmpty()) {
            truststoreCertificates.forEach((alias, cert) -> {
                CertificateRO certificateRO = convertToRo(cert);
                certificateRO.setAlias(alias);
                certificateROList.add(certificateRO);
            });
        }

        return certificateROList;
    }

    public CertificateRO convertToRo(X509Certificate d) {
        return conversionService.convert(d, CertificateRO.class);
    }

    /**
     * Extracts all Certificate Policy identifiers the "Certificate policy" extension of X.509.
     * If the certificate policy extension is unavailable, returns an empty list.
     *
     * @param cert a X509 certificate
     * @return the list of CRL urls of certificate policy identifiers
     */
    public List<String> getCertificatePolicyIdentifiers(X509Certificate cert) throws CertificateException {

        byte[] certPolicyExt = cert.getExtensionValue(org.bouncycastle.asn1.x509.Extension.certificatePolicies.getId());
        if (certPolicyExt == null) {
            return new ArrayList<>();
        }

        CertificatePolicies policies;
        try {
            policies = CertificatePolicies.getInstance(JcaX509ExtensionUtils.parseExtensionValue(certPolicyExt));
        } catch (IOException e) {
            throw new CertificateException("Error occurred while reading certificate policy object!", e);
        }

        return Arrays.stream(policies.getPolicyInformation())
                .map(PolicyInformation::getPolicyIdentifier)
                .map(ASN1ObjectIdentifier::getId)
                .map(StringUtils::trim)
                .collect(Collectors.toList());
    }

    /**
     * Method validates if the certificate contains one of allowed Certificate policy. At the moment it does not validates
     * the whole chain. Because in some configuration cases does not use the truststore
     *
     * @param certificate
     * @throws CertificateException
     */
    protected void validateCertificatePolicyMatchLegacy(X509Certificate certificate) throws CertificateException {

        // allowed list
        List<String> allowedCertificatePolicyOIDList = configurationService.getAllowedCertificatePolicies();
        if (allowedCertificatePolicyOIDList == null || allowedCertificatePolicyOIDList.isEmpty()) {
            LOG.debug("Certificate policy is not configured. Skip Certificate policy validation!");
            return;
        }
        // certificate list
        List<String> certPolicyList = getCertificatePolicyIdentifiers(certificate);
        if (certPolicyList.isEmpty()) {
            String excMessage = String.format("Certificate has empty CertificatePolicy extension. Certificate: %s ", certificate);
            throw new CertificateException(excMessage);
        }

        Optional<String> result = certPolicyList.stream().filter(allowedCertificatePolicyOIDList::contains).findFirst();
        if (result.isPresent()) {
            LOG.info("Certificate [{}] is trusted with certificate policy [{}]", certificate, result.get());
            return;
        }
        String excMessage = String.format("Certificate policy verification failed. Certificate [%s] does not contain any of the policy: [%s]", certificate, allowedCertificatePolicyOIDList);
        throw new CertificateException(excMessage);
    }

    protected void validateCertificateSubjectExpressionLegacy(X509Certificate signingCertificate) throws CertificateException {
        LOG.debug("Validate certificate subject");
        Pattern certSubjectExpression = configurationService.getCertificateSubjectRegularExpression();
        if (certSubjectExpression == null) {
            LOG.debug("Certificate subject regular expression is empty, verification is disabled.");
            return;
        }

        String subject = signingCertificate.getSubjectX500Principal().getName(X500Principal.RFC2253);
        if (!certSubjectExpression.matcher(subject).matches()) {
            String excMessage = String.format("Certificate subject [%s] does not match the regular expression configured [%s]", subject, certSubjectExpression);
            LOG.error(excMessage);
            throw new CertificateException(excMessage);
        }
    }
}
