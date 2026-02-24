package main.java.es.ucm.fdi.iw.model;
import jakarta.persistence.*;
import lombok.Data;
import java.util.List;
import lombok.NoArgsConstructor;

import es.ucm.fdi.iw.model.User;

import java.util.ArrayList;

@Entity
@Data
@NoArgsConstructor
public class Pedido{
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;

    @ManyToOne
    private User cliente; // Relación ManyToOne: un cliente puede tener muchos pedidos

    @OneToMany
    @JoinColumn(name = "pedido_id") // Evita la creación de una tabla intermedia
    private List<LineaPedido> lineas = new ArrayList<>();

   
}