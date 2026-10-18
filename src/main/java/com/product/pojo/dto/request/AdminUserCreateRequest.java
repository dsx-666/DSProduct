package com.product.pojo.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@NoArgsConstructor
public class AdminUserCreateRequest {
    private String email;
    private String password;
    private String name;
    private String role;
    private String brand;
    private String status;

    @JsonIgnore
    private boolean brandProvided;
    @JsonIgnore
    private final Map<String,Object> unsupportedFields = new LinkedHashMap<>();

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setBrand(String brand) {
        this.brandProvided = true;
        this.brand = brand;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @JsonAnySetter
    public void addUnsupportedField(String name, Object value) {
        unsupportedFields.put(name, value);
    }
}
