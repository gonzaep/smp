-- Copyright 2018 European Commission | CEF eDelivery
--
-- Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the European Commission - subsequent versions of the EUPL (the "Licence");
-- You may not use this work except in compliance with the Licence.
--
-- You may obtain a copy of the Licence attached in file: LICENCE-EUPL-v1.2.pdf
--
-- Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the Licence for the specific language governing permissions and limitations under the Licence.


CREATE TABLE smp_domain (
  domainId              VARCHAR(50)
                        CHARACTER SET utf8
                        COLLATE utf8_bin NOT NULL,
  bdmslClientCertHeader VARCHAR(4000)
                        CHARACTER SET utf8
                        COLLATE utf8_bin NULL,
  bdmslClientCertAlias  VARCHAR(50)
                        CHARACTER SET utf8
                        COLLATE utf8_bin NULL,
  bdmslSmpId            VARCHAR(50)
                        CHARACTER SET utf8
                        COLLATE utf8_bin NOT NULL,
  signatureCertAlias    VARCHAR(50)
                        CHARACTER SET utf8
                        COLLATE utf8_bin NULL,
  PRIMARY KEY(domainId)
);


INSERT INTO smp_domain(domainId, bdmslSmpId) VALUES('domain1', 'DEFAULT-SMP-ID');



ALTER TABLE smp_service_group ADD
  domainId  VARCHAR(50)
            CHARACTER SET utf8
            COLLATE utf8_bin NOT NULL
            DEFAULT 'domain1';

ALTER TABLE smp_service_group ADD
  CONSTRAINT
    FK_srv_group_domain FOREIGN KEY (domainId)
    REFERENCES smp_domain (domainId);




DELIMITER //

DROP PROCEDURE IF EXISTS validate_new_user //
CREATE PROCEDURE validate_new_user (IN new_user_is_admin TINYINT(1))
BEGIN
    IF new_user_is_admin <> 0 AND new_user_is_admin <> 1
    THEN
      SIGNAL SQLSTATE '99999'
      SET MESSAGE_TEXT = '0 or 1 are the only allowed values for ISADMIN column';
    END IF;
  END //

DROP PROCEDURE IF EXISTS validate_new_domain //
CREATE PROCEDURE validate_new_domain (IN new_bdmsl_client_cert_alias varchar(50), IN new_bdmsl_client_cert_header varchar(4000))
BEGIN
    IF ((new_bdmsl_client_cert_alias > '' OR new_bdmsl_client_cert_alias = null) AND (new_bdmsl_client_cert_header > '' OR new_bdmsl_client_cert_header = null))
    THEN
      SIGNAL SQLSTATE '99999'
      SET MESSAGE_TEXT = 'Both BDMSL authentication ways cannot be switched ON at the same time: bdmslClientCertAlias and bdmslClientCertHeader';
    END IF;
  END //


DROP TRIGGER IF EXISTS smp_domain_check_bdmsl_auth_before_insert //
DROP TRIGGER IF EXISTS smp_domain_check_bdmsl_auth_before_update //
CREATE TRIGGER smp_domain_check_bdmsl_auth_before_update
BEFORE UPDATE ON smp_domain
FOR EACH ROW
  BEGIN
    call validate_new_domain(NEW.bdmslClientCertAlias, NEW.bdmslClientCertHeader);
  END //
CREATE TRIGGER smp_domain_check_bdmsl_auth_before_insert
BEFORE INSERT ON smp_domain
FOR EACH ROW
  BEGIN
    call validate_new_domain(NEW.bdmslClientCertAlias, NEW.bdmslClientCertHeader);
  END //


DROP TRIGGER IF EXISTS smp_user_check_is_admin_value_before_insert //
DROP TRIGGER IF EXISTS smp_user_check_is_admin_value_before_update //

CREATE TRIGGER smp_user_check_is_admin_value_before_insert
BEFORE INSERT ON smp_user
FOR EACH ROW
  BEGIN
	call validate_new_user(NEW.ISADMIN);
  END //
CREATE TRIGGER smp_user_check_is_admin_value_before_update
BEFORE UPDATE ON smp_user
FOR EACH ROW
  BEGIN
	call validate_new_user(NEW.ISADMIN);
  END //

DELIMITER ;


commit;