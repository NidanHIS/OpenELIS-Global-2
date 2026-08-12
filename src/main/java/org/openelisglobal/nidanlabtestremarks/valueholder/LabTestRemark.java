package org.openelisglobal.nidanlabtestremarks.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;

/**
 * Entity for clinlims.lab_test_remarks. Maps entity_type ('TEST' or 'PANEL'),
 * entity_id, and remarks text. Test and panel names are dynamically resolved
 * via TestService/PanelService.
 */
@Entity
@Table(schema = "clinlims", name = "lab_test_remarks")
public class LabTestRemark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 'TEST' or 'PANEL' */
    @Column(name = "entity_type", nullable = false, length = 10)
    private String entityType;

    /** PK of clinlims.test or clinlims.panel */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "remarks", length = 2000)
    private String remarks;

    @Column(name = "lastupdated")
    private Timestamp lastupdated;

    @Column(name = "sys_user_id", length = 255)
    private String sysUserId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Timestamp getLastupdated() {
        return lastupdated;
    }

    public void setLastupdated(Timestamp lastupdated) {
        this.lastupdated = lastupdated;
    }

    public String getSysUserId() {
        return sysUserId;
    }

    public void setSysUserId(String sysUserId) {
        this.sysUserId = sysUserId;
    }
}
