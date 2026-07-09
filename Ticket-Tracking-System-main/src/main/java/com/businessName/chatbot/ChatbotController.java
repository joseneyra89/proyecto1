package com.businessName.chatbot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chatbot")
@CrossOrigin(origins = "*")
public class ChatbotController {

    @Autowired
    private ChatbotService chatbotService;

    @PostMapping("/enviar")
    public ChatbotResponse recibirMensaje(@RequestBody String mensajeUsuario) {
        String mensajeLimpio = mensajeUsuario.replace("\"", ""); 
        return chatbotService.procesarMensaje(mensajeLimpio);
    }
}
