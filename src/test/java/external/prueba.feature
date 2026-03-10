Feature: Flujo del administrador y el usuario

Background:

    * url baseUrl

Scenario: El administrador añade un plato en el gestor

    * call read('login.feature@login_a')

    Given driver baseUrl + '/gestor'

    And input ('input [name = nombreplato], 'Bocadillo de tortilla')
    And input ('input [name = descripcion], 'Esto es un plato de prueba con karate')
    And input ('input [name = precio], '5.00')
    And input ('input [name = imagen], 'Torilla-Patatas.png')

    When submit().click("form[action='/gestor/addPlato'] button")
    
    Then waitForUrl(baseUrl + '/gestor')


  Scenario: Un usuario normal hace una compra y envía un mensaje de contacto

    * call read('login.feature@login_b')

    Given driver baseUrl + '/plato'

    When submit().click("form[action='/carrito/add/'] button")
    
    Then waitForUrl(baseUrl + '/carrito')

    When submit().click("form[action='/carrito/comprar'] button")
    
    Then waitForUrl(baseUrl + '/inicio?pedidoRealizado=true')