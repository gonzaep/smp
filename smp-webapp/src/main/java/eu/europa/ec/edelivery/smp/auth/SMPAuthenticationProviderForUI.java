package eu.europa.ec.edelivery.smp.auth;

import eu.europa.ec.edelivery.smp.data.dao.UserDao;
import eu.europa.ec.edelivery.smp.data.model.DBUser;
import eu.europa.ec.edelivery.smp.data.ui.auth.SMPAuthority;
import eu.europa.ec.edelivery.smp.data.ui.enums.AlertSuspensionMomentEnum;
import eu.europa.ec.edelivery.smp.data.ui.enums.CredentialTypeEnum;
import eu.europa.ec.edelivery.smp.data.ui.enums.SMPPropertyEnum;
import eu.europa.ec.edelivery.smp.logging.SMPLogger;
import eu.europa.ec.edelivery.smp.logging.SMPLoggerFactory;
import eu.europa.ec.edelivery.smp.logging.SMPMessageCode;
import eu.europa.ec.edelivery.smp.services.AlertService;
import eu.europa.ec.edelivery.smp.services.CRLVerifierService;
import eu.europa.ec.edelivery.smp.services.ConfigurationService;
import eu.europa.ec.edelivery.smp.services.ui.UITruststoreService;
import eu.europa.ec.edelivery.smp.utils.SecurityUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Collections;
import java.util.Optional;

/**
 * Authentication provider for the UI authentication.
 *
 * @author Joze Rihtarsic
 * @since 4.2
 */
@Component
public class SMPAuthenticationProviderForUI implements AuthenticationProvider {

    private static final SMPLogger LOG = SMPLoggerFactory.getLogger(SMPAuthenticationProviderForUI.class);

    private static final BadCredentialsException BAD_CREDENTIALS_EXCEPTION = new BadCredentialsException("Login failed; Invalid userID or password");
    private static final BadCredentialsException SUSPENDED_CREDENTIALS_EXCEPTION = new BadCredentialsException("The user is suspended. Please try again later or contact your administrator.");

    final UserDao mUserDao;
    final ConversionService conversionService;
    final CRLVerifierService crlVerifierService;
    final UITruststoreService truststoreService;
    final ConfigurationService configurationService;
    final AlertService alertService;


    @Autowired
    public SMPAuthenticationProviderForUI(UserDao mUserDao,
                                          ConversionService conversionService,
                                          CRLVerifierService crlVerifierService,
                                          AlertService alertService,
                                          UITruststoreService truststoreService,
                                          ConfigurationService configurationService) {
        this.mUserDao = mUserDao;
        this.conversionService = conversionService;
        this.crlVerifierService = crlVerifierService;
        this.alertService = alertService;
        this.truststoreService = truststoreService;
        this.configurationService = configurationService;
    }

    @Override
    public Authentication authenticate(Authentication authenticationToken)
            throws AuthenticationException {

        Authentication authentication = null;
        // PreAuthentication token for the rest service certificate authentication
        LOG.debug("Authenticate authentication token type: [{}]", authenticationToken.getClass());
        if (authenticationToken instanceof UILoginAuthenticationToken) {
            authentication = authenticateByUsernamePassword((UILoginAuthenticationToken) authenticationToken);
        }
        return authentication;
    }

    public Authentication authenticateByUsernamePassword(UILoginAuthenticationToken auth)
            throws AuthenticationException {

        long startTime = Calendar.getInstance().getTimeInMillis();

        String username = auth.getName();
        String userCredentialToken = auth.getCredentials().toString();

        DBUser user;
        try {
            Optional<DBUser> oUsr = mUserDao.findUserByUsername(username);
            if (!oUsr.isPresent() || !oUsr.get().isActive() ){
                LOG.debug("User with username does not exists [{}], continue with next authentication provider");
                LOG.securityWarn(SMPMessageCode.SEC_INVALID_PASSWORD, "Username does not exits", username);
                delayResponse(startTime);
                throw BAD_CREDENTIALS_EXCEPTION;
            }
            user = oUsr.get();
        } catch (AuthenticationException ex) {
            LOG.securityWarn(SMPMessageCode.SEC_USER_NOT_AUTHENTICATED, username, ExceptionUtils.getRootCause(ex), ex);
            delayResponse(startTime);
            throw BAD_CREDENTIALS_EXCEPTION;

        } catch (RuntimeException ex) {
            LOG.securityWarn(SMPMessageCode.SEC_USER_NOT_AUTHENTICATED, username, ExceptionUtils.getRootCause(ex), ex);
            delayResponse(startTime);
            throw BAD_CREDENTIALS_EXCEPTION;
        }

        validateIfUserAccountIsSuspended(user, startTime);

        SMPAuthority authority = SMPAuthority.getAuthorityByRoleName(user.getRole());
        // the webservice authentication does not support session set the session secret is null!
        SMPUserDetails userDetails = new SMPUserDetails(user,
                SecurityUtils.generatePrivateSymmetricKey(),
                Collections.singletonList(authority));

        String role = user.getRole();
        SMPAuthenticationToken smpAuthenticationToken = new SMPAuthenticationToken(username, userCredentialToken,
                userDetails);
        try {
            if (!BCrypt.checkpw(userCredentialToken, user.getPassword())) {
                LOG.securityWarn(SMPMessageCode.SEC_INVALID_PASSWORD, username);
                loginAttemptForUserFailedAndThrowError(user, true, startTime);
            }
            user.setSequentialLoginFailureCount(0);
            user.setLastFailedLoginAttempt(null);
            mUserDao.update(user);
        } catch (IllegalArgumentException ex) {
            // password is not hashed;
            LOG.securityWarn(SMPMessageCode.SEC_INVALID_PASSWORD, ex, username);
            loginAttemptForUserFailedAndThrowError(user, true, startTime);
        }
        LOG.securityInfo(SMPMessageCode.SEC_USER_AUTHENTICATED, username, role);
        return smpAuthenticationToken;
    }


    public void delayResponse(long startTime) {
        int delayInMS = configurationService.getLoginFailDelayInMilliSeconds() - (int) (Calendar.getInstance().getTimeInMillis() - startTime);
        if (delayInMS > 0) {
            try {
                LOG.debug("Delay response for [{}] ms to mask password/username login failures!", delayInMS);
                Thread.sleep(delayInMS);
            } catch (InterruptedException ie) {
                LOG.debug("Thread interrupted during sleep.", ie);
                Thread.currentThread().interrupt();
            }
        }
    }

    public void loginAttemptForUserFailedAndThrowError(DBUser user, boolean notYetSuspended, long startTime) {
        user.setSequentialLoginFailureCount(user.getSequentialLoginFailureCount() != null ? user.getSequentialLoginFailureCount() + 1 : 1);
        user.setLastFailedLoginAttempt(OffsetDateTime.now());
        mUserDao.update(user);
        LOG.securityWarn(SMPMessageCode.SEC_INVALID_PASSWORD, user.getUsername());
        boolean isUserSuspended = user.getSequentialLoginFailureCount() >= configurationService.getLoginMaxAttempts();
        if (isUserSuspended) {
            LOG.info("User [{}] failed sequential attempt exceeded the max allowed attempts [{}]!", user.getUsername(), configurationService.getLoginMaxAttempts());
            // at notYetSuspended alert is sent for all settings AT_LOGON, WHEN_BLOCKED
            if (notYetSuspended ||
                    configurationService.getAlertBeforeUserSuspendedAlertMoment() == AlertSuspensionMomentEnum.AT_LOGON) {
                alertService.alertCredentialsSuspended(user, CredentialTypeEnum.USERNAME_PASSWORD);
            }
        } else {
            // always invoke the method. The method handles the smp.alert.user.login_failure.enabled
            alertService.alertCredentialVerificationFailed(user, CredentialTypeEnum.USERNAME_PASSWORD);
        }
        delayResponse(startTime);
        if (isUserSuspended) {
            throw SUSPENDED_CREDENTIALS_EXCEPTION;
        } else {
            throw BAD_CREDENTIALS_EXCEPTION;
        }
    }

    /**
     * Method tests if user account Suspended
     *
     * @param user
     */
    public void validateIfUserAccountIsSuspended(DBUser user, long startTime) {
        if (user.getSequentialLoginFailureCount() == null
                || user.getSequentialLoginFailureCount() < 0) {
            LOG.trace("User has no previous failed attempts");
            return;
        }
        if (configurationService.getLoginMaxAttempts() == null
                || configurationService.getLoginMaxAttempts() < 0) {
            LOG.warn("Max login attempts [{}] is not set", SMPPropertyEnum.USER_MAX_FAILED_ATTEMPTS.getProperty());
            return;
        }

        if (user.getLastFailedLoginAttempt() == null) {
            LOG.warn("User [{}] has failed attempts [{}] but null last Failed login attempt!", user.getUsername(), user.getLastFailedLoginAttempt());
            return;
        }
        // check if the last failed attempt is already expired. If yes just clear the attempts
        if (configurationService.getLoginSuspensionTimeInSeconds() != null && configurationService.getLoginSuspensionTimeInSeconds() > 0
                && ChronoUnit.SECONDS.between(user.getLastFailedLoginAttempt(), OffsetDateTime.now()) > configurationService.getLoginSuspensionTimeInSeconds()) {
            LOG.warn("User [{}] suspension is expired! Clear failed login attempts and last failed login attempt", user.getUsername());
            user.setLastFailedLoginAttempt(null);
            user.setSequentialLoginFailureCount(0);
            mUserDao.update(user);
            return;
        }

        if (user.getSequentialLoginFailureCount() < configurationService.getLoginMaxAttempts()) {
            LOG.debug("User [{}] failed login attempt [{}]! did not reach the max failed attempts [{}]", user.getUsername(), user.getSequentialLoginFailureCount(), configurationService.getLoginMaxAttempts());
            return;
        }
        LOG.securityWarn(SMPMessageCode.SEC_USER_SUSPENDED, user.getUsername());
        loginAttemptForUserFailedAndThrowError(user, false, startTime);
    }

    @Override
    public boolean supports(Class<?> auth) {
        LOG.info("Support authentication: " + auth);
        boolean supportAuthentication = auth.equals(UILoginAuthenticationToken.class);
        if (!supportAuthentication) {
            LOG.warn("SMP does not support authentication type: " + auth);
        }
        return supportAuthentication;
    }
}
