package com.FinancIA.api.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Una posición del portafolio del usuario: qué activo compró,
 * cuánto y a qué precio. El P&L se calcula con precios reales.
 */
@Entity
@Table(name = "posicion")
@Data
public class Posicion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email del usuario dueño (identidad del JWT). */
    private String email;

    /** Activo: ticker de Yahoo (YPF), nombre cripto (Bitcoin) o entidad de renta fija. */
    private String activo;

    /** Cantidad poseída (unidades para acciones/cripto; monto invertido en renta fija). */
    private double cantidad;

    /** Precio de compra por unidad. */
    private double precioCompra;

    private LocalDate fecha;
}
