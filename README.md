# Cumania Territories

Mod client-side (Fabric 1.21.11) que mostra os territórios dos países do site no **Xaero's World Map** e no **Xaero's Minimap**, como o Open Parties and Claims mostra os claims: um tom por país, uma linha nas fronteiras entre países, com precisão de bloco, e o nome do país (e da província) ao passar o mouse.

Funciona com um dos dois mods do Xaero's ou com os dois. Não precisa de nada no servidor.

## Como funciona

1. O site publica `gaia/territories.json`: as províncias do `latest.svg` já em coordenadas de bloco (`tools/export_territories.py`).
2. O mod baixa esse arquivo, guarda uma cópia em `config/cumania_territories_cache.json` (funciona offline) e o confere de novo ao entrar num mundo e a cada 30 minutos.
3. Cada bloco pertence à província que contém o centro dele (as províncias mais abaixo no SVG ficam por cima, como no site). O Xaero's pede 16x16 cores por chunk, e o mod responde bloco a bloco: preenchimento dentro do território e uma linha de 1 bloco onde o vizinho é de outro país. Dois mixins pequenos registram o nosso *highlighter* ao lado do dos claims, e o Xaero's cuida de desenhar, de dar zoom e de manter o cache.

**Memória:** um byte por bloco do retângulo dos territórios (cerca de 25 MB para um mapa de 5000x5000).

## Compilar

1. Copie os jars do Xaero's que você usa para `libs/` (só para compilar).
2. Rode `./gradlew build` (Windows: `gradlew build`) com Java 21. O jar fica em `build/libs/cumania-territories-1.0.0.jar`.
3. Se o Gradle reclamar que alguma versão não existe, atualize `loader_version`, `fabric_api_version` ou o `fabric-loom` em `build.gradle` com o que https://fabricmc.net/develop/ indica para o 1.21.11.

## Instalar

Ponha o jar na pasta `mods` junto com Fabric API e o Xaero's. Abra o jogo uma vez: o mod cria `config/cumania_territories.json`. A `url` já vem apontando para `https://bmcmc.online/gaia/territories.json`, então os jogadores não precisam configurar nada. Para outro site, troque `url` e rode `/territories reload`.

## Opções (`config/cumania_territories.json`)

| Opção | Padrão | O que faz |
|---|---|---|
| `url` | `https://bmcmc.online/gaia/territories.json` | Onde está o `territories.json` |
| `enabled` | `true` | Liga/desliga a camada |
| `dimension` | `overworld` | Dimensão dos territórios (`overworld`, `nether`, `end`) |
| `refreshMinutes` | `30` | De quanto em quanto tempo olhar o site (0 = só ao entrar) |
| `fillAlpha` | `90` | Opacidade do preenchimento (0-255) |
| `borderAlpha` | `230` | Opacidade da linha de fronteira (0-255) |
| `showProvince` | `true` | Mostrar também a província no tooltip |

## Comandos

`/territories` (liga/desliga) · `/territories reload` · `/territories status`

## Se o Xaero's atualizar

O mod depende de duas coisas internas do Xaero's: `HighlighterRegistry.end()` (onde nos registramos) e a classe `AbstractHighlighter` (World Map e Minimap). Se uma atualização mudar isso, o mod simplesmente não pinta nada e o log mostra o erro do mixin; basta recompilar contra o jar novo e ajustar os nomes.

## Testes

`test/IndexCheck.java` confere o índice de blocos contra um teste exato de ponto-em-polígono (marcadores do SVG e blocos aleatórios). Veja o comentário no topo do arquivo.


## Novo na 1.2.0: painel de informações e cidades

- **Clique esquerdo num país** no World Map (mapa grande do Xaero) abre um painel com tipo, nome oficial, formação, líder, governo, etnia, capital, população e diplomacia (suseranos, súditos, aliados, NAP, alianças, guerras). Clique no mar para fechar.
- **Cidades e localidades do site** aparecem como ícones com o nome da cor do país; o nome aparece conforme você dá zoom (cidades grandes primeiro). Clicar numa cidade mostra os dados dela e do país.
- Opções em `config/cumania_territories.json`: `showPanel`, `showLocalities`, `showLocalityNames`, `localityZoomBias` (aumente para ver mais cidades de longe).
- Os dados vêm do mesmo `territories.json`; rode `python tools/export_territories.py gaia` (ou deixe o GitHub Action fazer) para incluí-los. Mods antigos continuam compatíveis com o arquivo novo.
- Limitações: sem bandeiras/fontes do site; a diplomacia é uma lista simplificada. Painel e cidades só no World Map (não no minimapa).

## Novo na 1.2.1: painel redesenhado

- O painel tem **cabeçalho na cor do país, bandeira** (baixada do site e desenhada em pixels), seções e rótulos em maiúsculas. Se a tela for pequena, os itens menos importantes saem primeiro. Clicar no mar ou no "x" fecha o painel.
- Requer o `territories.json` novo (campo `flag` em cada país), gerado por `tools/export_territories.py`.

## 1.2.3

- Removido o destaque do país clicado no mapa (dava atraso por causa do Xaero). O painel, a bandeira e as cidades continuam iguais.

## 1.3.0: cartão do jogador

- Na lista de população do painel do país, cada nome é clicável. Abre ao lado um cartão com a **skin** do jogador, o nome no jogo, o país de origem, a última entrada, as afiliações e as notas — tudo vindo do `players.json` do site (nada é buscado fora do site).
- "Copy NameMC link" copia o link do perfil do jogador no NameMC.
- As skins são baixadas da Mojang pelo exportador (GitHub Action) e ficam em `gaia/skins/`; o mod só lê do site. Em `players.json` dá para escrever `"skin": "<link do NameMC, UUID ou nome>"` num jogador para escolher outra conta; sem isso vale o `ign`.
- Requer o `territories.json` novo (campo `players`) e as skins geradas por `tools/export_territories.py`.

## 1.3.1: mais dados no cartão do jogador

- O cartão agora segue a página do jogador do site: **afiliações atuais** (nação/clique e cargo), **purge timer** (dias, reino e população do reino), **última entrada**, **purgeable from / since** (calculado no jogo a partir da última entrada, então sempre atual), **imunidade**, **senhorios** (localidades de que é lorde, com tipo e designações), histórico e notas.
- Não mostra "Online now" (precisaria da lista de jogadores online do servidor).
- Requer o `territories.json` novo (rode o workflow): `current`, `purge` e `immune` por jogador, e `lord` das localidades agora aceita senhorio compartilhado.

## 1.3.2: ir até a cidade do lorde

- No cartão do jogador, cada localidade em "Lord of" é clicável: o mapa desliza até ela, com o mesmo movimento do "ir para coordenadas" do Xaero. O painel e o cartão continuam abertos.
- **1.3.3:** ao ir até a cidade o mapa também aproxima, até o zoom `goToZoom` (padrão 1.5, em `config/cumania_territories.json`; `0` desliga). Nunca afasta: se você já estiver mais perto, o zoom fica como está.

## 1.4.0: menu de guerra

Botão **WAR** no canto superior direito do World Map (ou `/territories war`). Tudo vem do `diplomacy.json` do site, via `territories.json`:

- **Wars**: guerras ativas e encerradas. Ao abrir uma, o menu mostra tipo e regra, status, início/fim (com "há X dias") e a trégua depois do fim, teatro, "no allies", os lados com seus membros (inclui "e subordinados") e as localidades capturadas (clique para ir até a cidade, com zoom). O mapa é pintado com as cores dos lados (países fora da guerra ficam cinza; províncias capturadas na cor de quem as tem; contestada em amarelo, sem controle em cinza claro). Voltar (`<`) ou fechar restaura o mapa.
- **Alliances**: alianças e pactos bilaterais, membros e datas de entrada; abrir pinta os membros.
- **Timers**: tréguas (inclusive a de 3 dias após guerra ou ultimato aceito), ultimatos pendentes (aceitos sozinhos após 72 h), NAPs com data, e diplolocks (Belligerent, Foundation, Set by Staff). Os tempos são calculados no jogo a partir de datas absolutas, então ficam certos sem recarregar.
- Opção `showWarMenu` no config. Requer o `territories.json` novo (bloco `war`): rode o workflow.
- Fora do menu (existe só no site): mapa estratégico com conexões, "em jogo"/alvos, controle por dynmap ao vivo, pontuação de capitulação e guerras de sucessão derivadas.

## 1.4.1: menu de guerra mais fácil de achar e de ler

- O botão **WAR** ficou maior, vermelho quando há guerras em andamento e mostra quantas (ex.: `WAR 1`).
- Painel mais largo, mais espaço entre linhas, abas em botões, títulos de seção com barra colorida, rótulos em cima e valores embaixo.
- Em Timers e nas listas, cada item usa duas linhas: quem (branco) e quando (dourado). Seções vazias aparecem só como "none".
- Rolagem por `[ Up ]` / `[ Down ]` quando o conteúdo passa da tela; `< Back` volta da guerra/aliança.

## 1.4.2: quem está ganhando

- Cada guerra ativa ganha uma **barra dividida entre os lados** (na lista e no topo do detalhe), proporcional ao que cada lado tomou do outro: cidades capturadas que estão em poder de um lado e pertencem a países do lado oposto. A linha abaixo diz quem está na frente ("Attackers are ahead"), empate ou "No captures yet".
- É uma medida simples (conta de capturas); a pontuação oficial de capitulação (7.9) continua só no site.

## 1.5.0: ajustes de desempenho e de interface

- **Cores da guerra mais rápidas:** ao abrir, trocar ou fechar uma guerra/aliança só as regiões do mapa cujas cores realmente mudam são redesenhadas (de uma guerra para outra, quase nada), as mais próximas de você primeiro. Se ainda achar lento, desligue "Grey out others in wars" no menu BMC: aí só os países da guerra mudam.
- **Destaque da cidade:** ao ir até uma cidade (pelo cartão do jogador ou pela guerra) ela ganha uma moldura dourada e anéis que pulsam por 6 segundos, e o nome aparece mesmo com pouco zoom.
- **Textos dentro das caixas:** títulos, rótulos, nomes e linhas longas agora são cortados com "..." em vez de passar da caixa, e o cartão do jogador não sai mais da tela.
- **Alianças por país:** a aba Alianças começa só com um filtro. Escolha um país para ver seus aliados e as alianças dele (ou "All alliances" para a lista completa).
- **Botão BMC (logo do site) ao lado do WAR:** menu para ajustar a intensidade da cor dos territórios e das fronteiras (passos de 10%), mostrar/ocultar territórios, cidades, nomes, painel e província no tooltip. Tudo é salvo no config.
- **Marcadores das cidades:** usa os ícones do próprio site (`icons/<tipo>.png`, baixados na hora). "City markers" no menu BMC alterna para os quadrados simples de antes. Opções no config: `markerStyle` (`site` ou `simple`), `markerIconUrl`, `logoUrl`, `warGreyOthers`.

## 1.5.1

- Títulos em negrito eram medidos como texto normal e ainda passavam da caixa; agora são medidos em negrito e cortados com "...".
- O tooltip do mapa ("País - Província") passou da largura da tela com nomes longos: agora cabe na tela (só o país, e cortado se preciso).

## 1.6.0: cores de guerra instantâneas

- As cores de guerra/aliança deixaram de passar pelo Xaero's (que redesenha região por região, devagar): o mod agora desenha as cores **ele mesmo** por cima do World Map. Cada país é cortado em células de 4 blocos, as células da mesma cor viram retângulos e só os que aparecem na tela são preenchidos a cada quadro. Abrir, trocar ou fechar uma guerra é imediato.
- Como o mod precisa saber onde cada bloco está na tela (calculado a partir da câmera do Xaero's), a cada quadro ele confere isso com o bloco que o próprio Xaero's diz estar sob o mouse. Se algum dia não bater, o desenho próprio se desliga sozinho (aviso no log) e volta o método antigo, mais lento.
- Menu BMC: "Instant war colours" liga/desliga isso (config `warOverlay`).
- Diferenças: as cores de guerra aparecem só no World Map (o minimapa continua com as cores normais), as fronteiras são um pouco mais "quadradas" (células de 4 blocos) e as cores ficam por cima dos ícones das cidades, de leve.

## 1.6.1: menos peso no mapa

- O desenho próprio das cores de guerra (1.6.0) usava milhares de retângulos por quadro e derrubava o FPS. Agora ele guarda 5 níveis de detalhe (células de 4 a 64 blocos) e usa o mais grosso que ainda passa de ~6 pixels na tela: de ~5000 preenchimentos por quadro caiu para menos de 500 (menos ainda com zoom).
- A atualização automática (ao entrar no mundo e a cada 30 min) só manda o Xaero's repintar o mapa quando formas ou cores dos territórios realmente mudaram. Um export novo só com jogadores, guerras etc. não mexe mais no mapa.

## 1.6.2: volta a pintar só pelo Xaero's

- Removido o desenho próprio das cores de guerra (1.6.0/1.6.1): as cores de guerra e de aliança voltaram a ser pintadas pelo Xaero's, como na 1.5.x, sem nada por cima do mapa. A opção "Instant war colours" e `warOverlay` saíram.
- Mantido da 1.6.1: a atualização automática só manda o Xaero's repintar quando formas ou cores dos territórios mudaram.
- Mantido da 1.5.0: ao abrir/trocar uma guerra, só as regiões cujas cores mudam são repintadas (as mais próximas de você primeiro); desligar "Grey out others in wars" no menu BMC deixa isso bem mais rápido.

## 1.7.0: dynmap em tempo real

O mod passa a ler o dynmap do servidor pelo mesmo endereço que o site usa (`https://bmcmc.online/dynmap/`, que o site repassa ao servidor). Só consulta enquanto o World Map está aberto.

- **Jogadores online no mapa:** ponto verde com o nome do jogador no site (o nome do jogo é ligado ao do site pelo `ign` do `players.json`); clicar abre o cartão dele. Quem o dynmap esconde continua escondido. Atualiza a cada `dynmapRefreshSeconds` (padrão 5, mínimo 2).
- **Cartão do jogador:** linha **Online now at X, Z (click to go)**, que leva a câmera até ele, ou **Status: Offline**. Na lista de população do país, quem está online ganha um ponto verde.
- **Controle de guerra ao vivo:** guerras marcadas `"live": true` no `diplomacy.json` (sem `map`) leem no dynmap quem segura cada cidade, igual ao site (`js/warlive.js`): o marcador é achado pelo id `Nome(País)` (ou o id do mapa `marker__Nome-lp-País-rp-__Tipo`), pelo nome, ou pelo marcador mais próximo (até 8 blocos); o ícone diz quem segura: `...Red` = primeiro lado, `...Blue` = segundo, `...Green` = terceiro, `...Contested`, `...Uncontrolled`; ícone sem isso não muda nada. O que o dynmap diz vale por cima do `captured`. A lista de capturadas, o "quem está ganhando" e as cores do mapa usam isso, e atualizam quando os marcadores mudam (a cada 8 s com o menu da guerra aberto). A lista mostra "Live from dynmap".
- **Exportador do site:** `war.live` agora vai no `territories.json` (precisa rodar o workflow para a guerra aparecer como ao vivo).
- **Menu BMC:** "Online players" e "Live war control". Config: `dynmapUrl`, `dynmapWorld`, `dynmapRefreshSeconds`, `showOnlinePlayers`, `liveWar`.
- Só vale para o mundo principal (`world`). Marcadores de mar (sea tiles) não são lidos.
