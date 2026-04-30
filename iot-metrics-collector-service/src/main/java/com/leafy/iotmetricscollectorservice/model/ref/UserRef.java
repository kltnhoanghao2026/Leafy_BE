package com.leafy.iotmetricscollectorservice.model.ref;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class UserRef {

    @Id
    @Column(length = 255)
    private String id;
}
