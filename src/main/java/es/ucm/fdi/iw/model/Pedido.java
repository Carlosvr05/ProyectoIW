package es.ucm.fdi.iw.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;
import java.util.ArrayList;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class Pedido {

    public enum Estado {
        SOLICITADO,
        PREPARANDO,
        LISTO_PARA_RECOGER,
        ENTREGADO,
        CANCELADO,
        FINALIZADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen")
    @SequenceGenerator(name = "gen", sequenceName = "gen")
    private long id;

    @ManyToOne
    private User cliente; // Relación ManyToOne: un cliente puede tener muchos pedidos

    @OneToMany
    @JoinColumn(name = "pedido_id") // Evita la creación de una tabla intermedia
    private List<LineaPedido> lineas = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private Estado estado;

    private LocalDateTime fechaCompra = LocalDateTime.now();

    private Boolean visible = true;

    public double getTotal() {
        return lineas.stream()
                .mapToDouble(l -> l.getCantidad() * l.getPrecioUnitario())
                .sum();
    }
}