/*
 * Copyright 2017 European Commission | CEF eDelivery
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence attached in file: LICENCE-EUPL-v1.2.pdf
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package eu.europa.ec.edelivery.smp.error.exceptions;

import eu.europa.ec.edelivery.smp.exceptions.ErrorBusinessCode;

/**
 * Created by migueti on 13/01/2017.
 */
public class BadRequestException extends RuntimeException {
    private ErrorBusinessCode errorBusinessCode;

    public BadRequestException(ErrorBusinessCode errorBusinessCode, String sMsg) {
        super(sMsg);
        this.errorBusinessCode = errorBusinessCode;
    }

    public ErrorBusinessCode getErrorBusinessCode() {
        return errorBusinessCode;
    }
}
