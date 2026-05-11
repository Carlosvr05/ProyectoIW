# MenUni - Menús Universitarios UCM

Descripción del Proyecto
MenUni es una aplicación web diseñada para la comunidad universitaria de la Universidad Complutense de Madrid (UCM). El objetivo principal es permitir a los usuarios consultar y realizar pedidos de los platos ofrecidos por las cafeterías de las diferentes facultades, permitiendo ahorrar tiempo al realizar pedidos justo antes de finalizar las clases.

El sistema cuenta con los siguientes roles de usuario:

Usuario/Cliente: Dispone de un monedero o saldo virtual. Puede ver los platos disponibles, añadirlos a su carrito y comprarlos siempre que disponga de saldo suficiente.

Administrador: Tiene acceso a un panel de control global para la gestión del catálogo de productos y el control de los mensajes enviados por otros usuarios.

Gestor de la Cafetería: Encargado de la administración específica del catálogo de comida, permitiendo insertar nuevos platos al menú o eliminar aquellos que ya no estén disponibles.

## Estructura de la Base de Datos
![Esquema de la Base de Datos](bd.png)

## Estado de Implementación de las Vistas

A continuación se detalla el estado actual de las vistas del proyecto:

* **Login (`login.html`)**: **[Completada]** Permite el inicio de sesión seguro y redirige al usuario o al administrador a sus respectivas vistas según su rol.
* **Inicio (`inicio.html`) y Facultades (`facultades.html`)**: **[Completada]** Muestra el listado de las diferentes facultades de la UCM viendo cuales hay disponibles. Mientras que en la vista de inicio se muestra una descripcion de la pagina web, unos consejos interactivos saludables y por último los integrantes del equipo de desarrollo.
* **Plato (`plato.html`)**: **[En un estado avanzado]** Permite visualizar los detalles de un plato, agregarlo al carrito y procesar el pedido descontando el importe del saldo del usuario. En la vista de plato faltaría que el usuario pueda valorar el plato con estrellas de 1 hasta 5 para así tener el ranking de los platos actualizado y los clientes sepan cuales son los mejores platos.
* **Carrito (`carrito.html`)**: **[Completada]** Permite visulizar el carrito de un determinado cliente registrado en la aplicación con todos los platos que ha añadido previamente pudiendo visualizar la cantidad de platos que ha añadido su precio unitario y su precio total y al final el boton de comprar con el precio total de todos los articulos. Al darle al botón de comprar se le restará la cantidad total del carrito al dinero del usuario que esté haciendo el pedido. Sino tiene dinero saldrá un mensaje por pantalla de saldo insuficiente.
* **Panel de Gestor/Admin (`gestor.html` / `admin.html`)**: **[Completada]** Vista reservada para usuarios con rol de administrador. Permite la gestión del catálogo de productos (creación y eliminación de platos) y el control de los mensajes que otros usuarios mandan.
* **Perfil de Usuario (`user.html`)**: **[En proceso]** Muestra la información personal del usuario y su saldo actual disponible para realizar compras. Falta añadir un boton para insertar dinero en la cuenta del cliente.
* **Otras vistas (`ranking.html`, `contacto.html`)**: **[Completada]** Vista de ranking para ver los platos con mejores valoraciones o para ordenarlos por orden alfabético y la vista de contacto donde se puede enviar un mensaje como usuario al admin de la web sobre algún problema o alguna duda rellenando los campos de asunto y descripción.
