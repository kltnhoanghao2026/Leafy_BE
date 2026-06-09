package com.leafy.iotmetricscollectorservice.model.ref;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "farm_plots")
public class FarmPlotRef {

    @Id
    private String id;
}
