package es.ucm.fdi.iw.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Valoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Plato plato;

    @ManyToOne
    private Facultad facultad;

    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(5)
    private int puntuacion;
}
