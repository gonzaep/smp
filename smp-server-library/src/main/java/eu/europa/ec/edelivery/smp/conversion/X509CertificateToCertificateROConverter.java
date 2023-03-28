package eu.europa.ec.edelivery.smp.conversion;

import eu.europa.ec.edelivery.security.PreAuthenticatedCertificatePrincipal;
import eu.europa.ec.edelivery.security.utils.X509CertificateUtils;
import eu.europa.ec.edelivery.smp.data.ui.CertificateRO;
import eu.europa.ec.edelivery.smp.exceptions.ErrorCode;
import eu.europa.ec.edelivery.smp.exceptions.SMPRuntimeException;
import eu.europa.ec.edelivery.smp.logging.SMPLogger;
import eu.europa.ec.edelivery.smp.logging.SMPLoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.TimeZone;

/**
 * @author Joze Rihtarsic
 * @since 4.1
 */
@Component
public class X509CertificateToCertificateROConverter implements Converter<X509Certificate, CertificateRO> {

    private static final SMPLogger LOG = SMPLoggerFactory.getLogger(CertificateROToDBCertificateConverter.class);
    private static final String S_CLIENT_CERT_DATEFORMAT = "MMM dd HH:mm:ss yyyy";
    // the GMT date format for the Client-Cert header generation!
    private static final ThreadLocal<DateFormat> dateFormatGMT = ThreadLocal.withInitial(() -> {
                SimpleDateFormat sdf = new SimpleDateFormat(S_CLIENT_CERT_DATEFORMAT);
                sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                return sdf;
            }
    );


    @Override
    public CertificateRO convert(X509Certificate cert) {

        PreAuthenticatedCertificatePrincipal data = X509CertificateUtils.extractPrincipalFromCertificate(cert);
        String subject = data.getSubjectOriginalDN();
        String issuer = data.getIssuerOriginalDN();
        String serial = data.getCertSerial();
        String certId = data.getName();

        String url = X509CertificateUtils.getCrlDistributionUrl(cert);
        CertificateRO cro = new CertificateRO();
        cro.setCertificateId(certId);
        cro.setSubject(subject);
        cro.setIssuer(issuer);
        cro.setCrlUrl(url);
        // set serial as HEX
        cro.setSerialNumber(serial);
        cro.setValidFrom(cert.getNotBefore());
        cro.setValidTo(cert.getNotAfter());
        try {
            cro.setEncodedValue(Base64.getMimeEncoder().encodeToString(cert.getEncoded()));
        } catch (CertificateEncodingException cex) {
            throw new SMPRuntimeException(ErrorCode.CERTIFICATE_ERROR, cex,
                    "Error occurred while decoding certificate " + subject, cex.getMessage(), cex);

        }
        // generate clientCertHeader header
        DateFormat sdf = dateFormatGMT.get();
        StringWriter sw = new StringWriter();
        sw.write("sno=");
        sw.write(serial);
        sw.write("&subject=");
        sw.write(urlEncodeString(subject));
        sw.write("&validfrom=");
        sw.write(urlEncodeString(sdf.format(cert.getNotBefore()) + " GMT"));
        sw.write("&validto=");
        sw.write(urlEncodeString(sdf.format(cert.getNotAfter()) + " GMT"));
        sw.write("&issuer=");
        sw.write(urlEncodeString(issuer));
        cro.setClientCertHeader(sw.toString());
        return cro;
    }

    private String urlEncodeString(String val) {
        if (StringUtils.isBlank(val)) {
            return "";
        } else {
            try {
                return URLEncoder.encode(val, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOG.error("Error occurred while url encoding the certificate string:" + val, e);
            }
        }
        return "";
    }
}
