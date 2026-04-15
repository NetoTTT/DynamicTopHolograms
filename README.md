# DynamicTopHolograms

**DynamicTopHolograms** é um plugin leve e altamente configurável que permite exibir **placares de líderes dinâmicos** em hologramas, usando valores de **PlaceholderAPI**. Ele se integra perfeitamente com o plugin **DecentHolograms** para renderizar hologramas visuais em tempo real com dados atualizados automaticamente.

---

## ✨ Recursos

- Exiba o **Top N jogadores** com base em qualquer placeholder do PlaceholderAPI (ex: dinheiro, kills, votos, etc)
- Totalmente configurável: título, formato das linhas e uso de cores (`&`)
- Gerenciamento completo via comandos dentro do jogo
- Suporte a ordenação crescente ou decrescente
- Compatível com múltiplos mundos e posicionamento preciso
- Integração com [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) e [DecentHolograms](https://www.spigotmc.org/resources/decent-holograms-1-8-1-20-1.96927/)

---

## 🧱 Dependências

- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) (obrigatório)
- [DecentHolograms](https://www.spigotmc.org/resources/decent-holograms-1-8-1-20-1.96927/) (obrigatório)
- **Servidor Compatível**: Spigot, Paper ou forks na versão **1.21.1+** (Requer Java 21)

---

## 📦 Exemplo de Uso

```bash
# Cria um holograma com o título "Top 10 Richest EliteMobs Players"
/dth create moneyTop Top 10 Richest EliteMobs Players

# Define o conteúdo do holograma usando o placeholder do EliteMobs
/dth set moneyTop %elitemobs_player_money% 10 &6#{rank} &7{player}: &a${value}

# Move o holograma para a posição atual do jogador
/dth movehere moneyTop
