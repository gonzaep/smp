package eu.europa.ec.edelivery.smp.data.ui.auth;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import eu.europa.ec.edelivery.smp.data.ui.databind.SMPAuthorityDeserializer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;


/**
 * @author Joze Rihtarsic
 * @since 4.1
 */
@JsonDeserialize(using = SMPAuthorityDeserializer.class)
public class SMPAuthority implements GrantedAuthority {

    // static constants for annotations!
    public static final String S_AUTHORITY_TOKEN_WS_SMP_ADMIN = "ROLE_WS_SMP_ADMIN";
    public static final String S_AUTHORITY_TOKEN_WS_SERVICE_GROUP_ADMIN = "ROLE_WS_SERVICE_GROUP_ADMIN";
    // ui
    public static final String S_AUTHORITY_TOKEN_SYSTEM_ADMIN = "ROLE_SYSTEM_ADMIN";
    public static final String S_AUTHORITY_TOKEN_SMP_ADMIN = "ROLE_SMP_ADMIN";
    public static final String S_AUTHORITY_TOKEN_SERVICE_GROUP_ADMIN = "ROLE_SERVICE_GROUP_ADMIN";

    // static constants for verification...
    public static final SMPAuthority S_AUTHORITY_SYSTEM_ADMIN = new SMPAuthority(SMPRole.SYSTEM_ADMIN.getCode());
    public static final SMPAuthority S_AUTHORITY_SMP_ADMIN = new SMPAuthority(SMPRole.SMP_ADMIN.getCode());
    public static final SMPAuthority S_AUTHORITY_SERVICE_GROUP = new SMPAuthority(SMPRole.SERVICE_GROUP_ADMIN.getCode());
    public static final SMPAuthority S_AUTHORITY_ANONYMOUS = new SMPAuthority(SMPRole.ANONYMOUS.getCode());

    public static final SMPAuthority S_AUTHORITY_WS_SYSTEM_ADMIN = new SMPAuthority(SMPRole.WS_SYSTEM_ADMIN.getCode());
    public static final SMPAuthority S_AUTHORITY_WS_SMP_ADMIN = new SMPAuthority(SMPRole.WS_SMP_ADMIN.getCode());
    public static final SMPAuthority S_AUTHORITY_WS_SERVICE_GROUP = new SMPAuthority(SMPRole.WS_SERVICE_GROUP_ADMIN.getCode());

    String role;

    private SMPAuthority(String role) {
        this.role = role;
    }

    @Override
    @JsonValue
    public String getAuthority() {
        return "ROLE_" + role;
    }

    public String getRole() {
        return role;
    }

    public static SMPAuthority getAuthorityByRoleName(String name) {
        if (StringUtils.isBlank(name)) {
            return S_AUTHORITY_ANONYMOUS;
        }
        SMPRole role = SMPRole.valueOf(name);
        return getAuthorityByRole(role);
    }

    public static SMPAuthority getAuthorityByRole(SMPRole role) {
        switch (role) {
            case SMP_ADMIN:
                return S_AUTHORITY_SMP_ADMIN;
            case SYSTEM_ADMIN:
                return S_AUTHORITY_SYSTEM_ADMIN;
            case SERVICE_GROUP_ADMIN:
                return S_AUTHORITY_SERVICE_GROUP;
            case WS_SMP_ADMIN:
                return S_AUTHORITY_WS_SMP_ADMIN;
            case WS_SERVICE_GROUP_ADMIN:
                return S_AUTHORITY_WS_SERVICE_GROUP;
            case WS_SYSTEM_ADMIN:
                return S_AUTHORITY_WS_SYSTEM_ADMIN;
            default:
                return S_AUTHORITY_ANONYMOUS;
        }
    }
}
