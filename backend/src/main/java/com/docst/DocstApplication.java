package com.docst;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration;

@SpringBootApplication(exclude = {
    OllamaAutoConfiguration.class
})
public class DocstApplication {
  public static void main(String[] args) {
    SpringApplication.run(DocstApplication.class, args);
  }
}
