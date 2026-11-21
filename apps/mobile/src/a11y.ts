// Ajuda de acessibilidade: faz um elemento com role de botão responder ao teclado como um <button>.
import type { KeyboardEvent } from "react";

/** Enter ou espaço disparam a ação, como num botão de verdade. */
export function onActivateKey(action: () => void) {
  return (event: KeyboardEvent) => {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      action();
    }
  };
}
