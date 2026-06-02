package com.mercado.orcamento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MercadoOrcamentoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MercadoOrcamentoApplication.class, args);
	}

}
