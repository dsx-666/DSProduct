package com.product.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@NoArgsConstructor
public class AdminUserUpdateRequest {
    private Long version;
    private String name;
    private String role;
    private String brand;
    private String status;

    @JsonIgnore
    private boolean nameProvided;
    @JsonIgnore
    private boolean roleProvided;
    @JsonIgnore
    private boolean brandProvided;
    @JsonIgnore
    private boolean statusProvided;
    @JsonIgnore
    private final Map<String,Object> unsupportedFields = new LinkedHashMap<>();

    public void setVersion(Long version) {
        this.version = version;
    }

    public void setName(String name) {
        this.nameProvided = true;
        this.name = name;
    }

    public void setRole(String role) {
        this.roleProvided = true;
        this.role = role;
    }

    public void setBrand(String brand) {
        this.brandProvided = true;
        this.brand = brand;
    }

    public void setStatus(String status) {
        this.statusProvided = true;
        this.status = status;
    }

    @JsonAnySetter
    public void addUnsupportedField(String name, Object value) {
        unsupportedFields.put(name, value);
    }

    @JsonIgnore
    public boolean hasMutableField() {
        return nameProvided || roleProvided || brandProvided || statusProvided;
    }
}
