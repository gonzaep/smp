package eu.europa.ec.edelivery.smp.auth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * UI login authentication token. The token is generated by SMPAuthenticationService and is supported by the SMPAuthenticationProviderForUI.
 * It is "distinguished" from UsernamePasswordAuthenticationToken, generated by basic authentication,
 * which is used for stateless web services invocation using the Personal Access Token credentials.
 *
 * @author Joze Rihtarsic
 * @since 4.2
 */
public class UILoginAuthenticationToken   extends UsernamePasswordAuthenticationToken {

    public UILoginAuthenticationToken(Object principal, Object credentials) {
        super(principal, credentials);
    }
}