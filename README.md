# ⚡ ZEUS Optimizer (Android)

App nativo para Android 8.0 ou mais novo. Não precisa de root.

## O que ele faz

**Painel**
- Nota de saúde de 0 a 100 (armazenamento, RAM, temperatura e saúde da bateria)
- Uso do armazenamento e da RAM, com barras
- Botão para encerrar apps parados em segundo plano
- Dicas que funcionam de verdade

**Limpeza**: varre o armazenamento e separa em:
| Categoria | Classificação |
|---|---|
| Instaladores .apk | SEGURO |
| Miniaturas (.thumbnails) | SEGURO |
| Temporários, downloads incompletos e logs | SEGURO |
| Lixeira de fotos (.trashed) | SEGURO |
| Pastas vazias | SEGURO |
| Arquivos duplicados (mantém o mais antigo) | REVISAR |
| Mídia enviada pelo WhatsApp | REVISAR |
| Backups antigos do WhatsApp (mantém o atual) | REVISAR |
| Downloads com mais de 90 dias | REVISAR |
| Arquivos com mais de 100 MB | REVISAR |

O que é SEGURO já vem marcado. O que é REVISAR você marca se quiser.
Nada é apagado sem confirmação. `Android/data` e `Android/obb` nunca são tocadas.

**Apps**
- Mostra há quantos dias cada app não é aberto e quanto espaço ocupa
- Marca **REMOVER** (60+ dias sem uso), **REVISAR** (30+ dias ou cache alto) e **MANTER**
- Teclados, a tela inicial e os apps do sistema nunca são sugeridos para remoção
- Botões para desinstalar e para abrir a tela de limpar o cache

## Limites do Android (sem root)
- Nenhum app pode limpar o cache de outros apps sozinho desde o Android 6. O app abre a tela certa e você toca em "Limpar cache".
- Nenhum app pode desinstalar outro sem você confirmar.
- Não existe "turbo de RAM" de verdade: o Android já gerencia a memória. O que deixa o celular lento quase sempre é armazenamento cheio e apps demais.

## Como gerar o APK
1. Crie um repositório no GitHub e envie esta pasta inteira.
2. O GitHub Actions compila sozinho (aba **Actions**, uns 5 minutos).
3. Vá em **Releases**, baixe `ZEUS-Optimizer.apk` pelo celular e instale (permita "instalar apps desconhecidos").

Ou abra a pasta no Android Studio e use **Build › Build APK(s)**.
