package eu.europa.ec.edelivery.smp.data.ui;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class DeleteEntityValidation implements Serializable {

    private static final long serialVersionUID = -4971552086560325308L;


    boolean validOperation;
    String stringMessage;
    List<String> listIds= new ArrayList<>();
    List<String> listDeleteNotPermitedIds = new ArrayList<>();

    public boolean isValidOperation() {
        return validOperation;
    }

    public void setValidOperation(boolean validOperation) {
        this.validOperation = validOperation;
    }

    public String getStringMessage() {
        return stringMessage;
    }

    public void setStringMessage(String stringMessage) {
        this.stringMessage = stringMessage;
    }

    public List<String> getListIds() {
        return listIds;
    }


    public List<String> getListDeleteNotPermitedIds() {
        return listDeleteNotPermitedIds;
    }


}
