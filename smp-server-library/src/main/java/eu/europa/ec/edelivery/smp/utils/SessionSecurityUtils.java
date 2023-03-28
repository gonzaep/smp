package eu.europa.ec.edelivery.smp.utils;

import eu.europa.ec.edelivery.smp.auth.SMPAuthenticationToken;
import eu.europa.ec.edelivery.smp.auth.SMPUserDetails;
import eu.europa.ec.edelivery.smp.logging.SMPLogger;
import eu.europa.ec.edelivery.smp.logging.SMPLoggerFactory;
import org.springframework.security.cas.authentication.CasAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class provides common session security tools to enhance UI security.
 * Session is enabled only for UI. To increase security SMP encrypt sensitive data. The
 * class provides tools for encrypting, decrypting data for the session.
 *
 * @author Joze Rihtarsic
 * @since 4.2
 */
public class SessionSecurityUtils {
    private static final SMPLogger LOG = SMPLoggerFactory.getLogger(SessionSecurityUtils.class);

    /**
     * '
     * Currently authentication tokens supported to create na UI session.
     */
    protected static final List<Class> sessionAuthenticationClasses = Arrays.asList(SMPAuthenticationToken.class,
            CasAuthenticationToken.class);

    /**
     * SMP uses entity ids type long. Because the keys are sequence keys, SMP encrypts ids for the User.
     *
     * @param id
     * @return
     */
    public static String encryptedEntityId(Long id) {
        if (id == null) {
            return null;
        }
        SecurityUtils.Secret secret = getAuthenticationSecret();
        String idValue = id.toString();
        return secret != null ? SecurityUtils.encryptURLSafe(secret, idValue) : idValue;
    }


    public static Long decryptEntityId(String id) {
        if (id == null) {
            return null;
        }
        SecurityUtils.Secret secret = getAuthenticationSecret();
        String value = secret != null ? SecurityUtils.decryptUrlSafe(secret, id) : id;
        return new Long(value);
    }

    public static Authentication getSessionAuthentication() {
        if (SecurityContextHolder.getContext() == null) {
            LOG.warn("No Security context!");
            return null;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            LOG.warn("No active Authentication!");
            return null;
        }

        if (getSessionAuthenticationClasses().contains(authentication.getClass())) {
            return authentication;
        }

        LOG.warn("Authentication class [{}] is not session enabled class types: [{}]!", authentication.getClass(),
                getSessionAuthenticationClasses().stream().map(Class::getName).collect(Collectors.toList()));
        return null;
    }

    public static SMPUserDetails getSessionUserDetails() {

        Authentication authentication = getSessionAuthentication();
        if (authentication == null) {
            LOG.warn("No active SMP session Authentication!");
            return null;
        }

        if (authentication instanceof SMPAuthenticationToken) {
            LOG.debug("Return user details from SMPAuthenticationToken");
            return ((SMPAuthenticationToken) authentication).getUserDetails();
        }

        if (authentication instanceof CasAuthenticationToken) {
            LOG.debug("Return session secret from CasAuthenticationToken");
            return (SMPUserDetails) ((CasAuthenticationToken) authentication).getUserDetails();
        }

        LOG.warn("Authentication class [{}] is not session enabled class types: [{}]!", authentication.getClass(),
                getSessionAuthenticationClasses().stream().map(Class::getName).collect(Collectors.toList()));
        return null;
    }

    public static SecurityUtils.Secret getAuthenticationSecret() {
        SMPUserDetails smpUserDetails = getSessionUserDetails();
        if (smpUserDetails == null) {
            LOG.warn("No SMPUserDetails object!");
            return null;
        }
        return smpUserDetails.getSessionSecret();
    }

    /**
     * Method returns authentication name  for logging
     *
     * @return authentication name
     */
    public static String getAuthenticationName() {
        if (SecurityContextHolder.getContext() == null) {
            LOG.debug("No Security context!");
            return null;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            LOG.debug("No active Authentication!");
            return null;
        }
        return authentication.getName();
    }

    public static List<Class> getSessionAuthenticationClasses() {
        return sessionAuthenticationClasses;
    }
}
