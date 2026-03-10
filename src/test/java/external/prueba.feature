Feature: Flujo del administrador y el usuario

Background:

    * url baseUrl

Scenario: El administrador añade un plato en el gestor

    # Llamamos al test de login de a 
    * call read('login.feature@login_a')

    # Navegamos hasta /gestor como admin
    Given driver baseUrl + '/gestor'

    # Como admin completamos la información de un plato (nombre,descripcion,precio, imagen)
    And input ('input [name = nombreplato], 'Bocadillo de tortilla')
    And input ('input [name = descripcion], 'Esto es un plato de prueba con karate')
    And input ('input [name = precio], '5.00')
    And input ('input [name = imagen], 'Torilla-Patatas.png')

    #Ahora pulsamos en el botón de añadir plato que activa la th:action /gestor/addPlato
    When submit().click("form[action='/gestor/addPlato'] button")
    
    #Esperamos a que nos redirija a la pantalla de gestor con el nuevo plato insertado
    Then waitForUrl(baseUrl + '/gestor')


  Scenario: Un usuario normal hace una compra y envía un mensaje de contacto

    # LLamamos al test de login de b
    * call read('login.feature@login_b')

    #Ahora navegamos a la ventana plato
    Given driver baseUrl + '/plato'

    # Ahora pulsamos como usuario en el boton de añadir un plato que activa la th:action /carrito/add
    When submit().click("form[action='/carrito/add/'] button")
    
    # Esperamos que nos redirija al carrito
    Then waitForUrl(baseUrl + '/carrito')

    # Ahora pulsamos en el botón del carrito de comprar 
    When submit().click("form[action='/carrito/comprar'] button")

    # Esperamos a que nos redirija al inicio con la variable de pedidoRealizado= true para que salga un mensaje con pedido confirmado
    Then waitForUrl(baseUrl + '/inicio?pedidoRealizado=true')