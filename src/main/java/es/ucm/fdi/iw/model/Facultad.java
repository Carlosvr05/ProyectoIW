package es.ucm.fdi.iw.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
public class Facultad {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    private String ubicacion; 
    
    private String descripcion; 
    
    private String horario; 
    
    private String aforo; 

    // Una facultad tiene muchos platos disponibles
    @ManyToMany(mappedBy = "facultades")
    private List<Plato> platos = new ArrayList<>();

    //Para imprimir el nombre directamente en la vista
    @Override
    public String toString() {
        return nombre;
    }
}