package com.leafy.iotmetricscollectorservice.model.ref;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "files")
public class FileRef {

    @Id
    private String id;
}
