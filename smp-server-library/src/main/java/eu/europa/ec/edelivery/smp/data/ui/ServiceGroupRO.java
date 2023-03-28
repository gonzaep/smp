package eu.europa.ec.edelivery.smp.data.ui;


import eu.europa.ec.edelivery.smp.data.ui.enums.EntityROStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Joze Rihtarsic
 * @since 4.1
 */


public class ServiceGroupRO extends BaseRO {


    private static final long serialVersionUID = -7523221767041516157L;
    private Long id;
    private String participantIdentifier;
    private String participantScheme;
    private boolean smlRegistered = false;
    private List<UserRO> lstUser = new ArrayList<>();
    private List<ServiceGroupDomainRO> serviceGroupDomains = new ArrayList<>();
    // for UI  service groups are  in one list.
    private List<ServiceMetadataRO> serviceMetadata = new ArrayList<>();
    private int extensionStatus = EntityROStatus.PERSISTED.getStatusNumber();
    private String extension;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getParticipantIdentifier() {
        return participantIdentifier;
    }

    public void setParticipantIdentifier(String participantIdentifier) {
        this.participantIdentifier = participantIdentifier;
    }

    public String getParticipantScheme() {
        return participantScheme;
    }

    public void setParticipantScheme(String participantScheme) {
        this.participantScheme = participantScheme;
    }

    public boolean isSmlRegistered() {
        return smlRegistered;
    }

    public void setSmlRegistered(boolean smlRegistered) {
        this.smlRegistered = smlRegistered;
    }

    public int getExtensionStatus() {
        return extensionStatus;
    }

    public void setExtensionStatus(int extensionStatus) {
        this.extensionStatus = extensionStatus;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public List<UserRO> getUsers() {
        return lstUser;
    }

    public List<ServiceGroupDomainRO> getServiceGroupDomains() {
        return serviceGroupDomains;
    }

    public List<ServiceMetadataRO> getServiceMetadata() {
        return serviceMetadata;
    }
}
