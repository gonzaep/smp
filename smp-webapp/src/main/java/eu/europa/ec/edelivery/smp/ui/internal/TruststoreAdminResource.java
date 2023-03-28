package eu.europa.ec.edelivery.smp.ui.internal;

import eu.europa.ec.edelivery.security.utils.X509CertificateUtils;
import eu.europa.ec.edelivery.smp.data.ui.auth.SMPAuthority;
import eu.europa.ec.edelivery.smp.data.ui.CertificateRO;
import eu.europa.ec.edelivery.smp.data.ui.KeystoreImportResult;
import eu.europa.ec.edelivery.smp.data.ui.ServiceResult;
import eu.europa.ec.edelivery.smp.exceptions.SMPRuntimeException;
import eu.europa.ec.edelivery.smp.logging.SMPLogger;
import eu.europa.ec.edelivery.smp.logging.SMPLoggerFactory;
import eu.europa.ec.edelivery.smp.services.PayloadValidatorService;
import eu.europa.ec.edelivery.smp.services.ui.UITruststoreService;
import eu.europa.ec.edelivery.smp.ui.ResourceConstants;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * @author Joze Rihtarsic
 * @since 4.1
 */
@RestController
@RequestMapping(value = ResourceConstants.CONTEXT_PATH_INTERNAL_TRUSTSTORE)
public class TruststoreAdminResource {

    private static final SMPLogger LOG = SMPLoggerFactory.getLogger(TruststoreAdminResource.class);


    private final UITruststoreService uiTruststoreService;
    private final PayloadValidatorService payloadValidatorService;

    public TruststoreAdminResource(UITruststoreService uiTruststoreService, PayloadValidatorService payloadValidatorService) {
        this.uiTruststoreService = uiTruststoreService;
        this.payloadValidatorService = payloadValidatorService;
    }

    @PutMapping(produces = {"application/json"})
    @RequestMapping(method = RequestMethod.GET)
    @Secured({SMPAuthority.S_AUTHORITY_TOKEN_SYSTEM_ADMIN})
    public ServiceResult<CertificateRO> getCertificateList() {
        List<CertificateRO> lst = uiTruststoreService.getCertificateROEntriesList();
        // clear encoded value to reduce http traffic
        int i =0;
        for (CertificateRO certificateRO : lst) {
            certificateRO.setEncodedValue(null);
            certificateRO.setIndex(i++);
        }

        ServiceResult<CertificateRO> sg = new ServiceResult<>();
        sg.getServiceEntities().addAll(lst);
        sg.setCount((long) lst.size());
        return sg;
    }


    @PreAuthorize("@smpAuthorizationService.isSystemAdministrator and @smpAuthorizationService.isCurrentlyLoggedIn(#userId)")
    @PostMapping(value = "/{user-id}/upload-certificate", consumes = MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE, produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public CertificateRO uploadCertificate(@PathVariable("user-id") String userId,
                                           @RequestBody byte[] fileBytes) {
        LOG.info("Got certificate cert size: {}", fileBytes.length);

        // validate content
        payloadValidatorService.validateUploadedContent(new ByteArrayInputStream(fileBytes), MimeTypeUtils.APPLICATION_OCTET_STREAM_VALUE);

        X509Certificate x509Certificate;
        CertificateRO certificateRO=null;
        try {
            x509Certificate = X509CertificateUtils.getX509Certificate(fileBytes);
        } catch (SMPRuntimeException | CertificateException e) {
            LOG.error("Error occurred while parsing certificate.", e);
            return certificateRO;
        }
        try {
            String alias = uiTruststoreService.addCertificate(null, x509Certificate);
            certificateRO = uiTruststoreService.convertToRo(x509Certificate);
            certificateRO.setAlias(alias);
        } catch (NoSuchAlgorithmException |  KeyStoreException | IOException |CertificateException e) {
            LOG.error("Error occurred while parsing certificate.", e);
            return certificateRO;
        }
        return certificateRO;
    }


    @DeleteMapping(value = "/{id}/delete/{alias}", produces = {"application/json"})
    @PreAuthorize("@smpAuthorizationService.systemAdministrator && @smpAuthorizationService.isCurrentlyLoggedIn(#userId)")
    public KeystoreImportResult deleteCertificate(@PathVariable("id") String userId,
                                               @PathVariable("alias") String alias) {
        LOG.info("Remove alias by user id {}, alias {}.", userId, alias);
        KeystoreImportResult keystoreImportResult = new KeystoreImportResult();

        try {
            uiTruststoreService.deleteCertificate(alias);
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            String msg = e.getClass().getName() +" occurred while reading the truststore: " + e.getMessage();
            LOG.error(msg, e);
            keystoreImportResult.setErrorMessage(msg);
        }

        return keystoreImportResult;
    }
}
