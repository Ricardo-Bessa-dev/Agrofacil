// Liga as tarefas assíncronas (envio de push) e as rotinas agendadas.
package br.edu.ifba.agrofacil.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Há uma só instância da API, então as rotinas não precisam de lock distribuído (doc 2 §1.12). */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {}
