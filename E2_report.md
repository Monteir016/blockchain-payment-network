# DOCUMENTO QUE ACOMPANHA A 2ª ENTREGA DO PROJETO DE SD

1) Quais dos seguintes requisitos foram corretamente resolvidos?
Abaixo de cada requisito, respondam: "Sim" ou "Não" ou "Sim mas com erros: [enumerar sucintamente os erros]"

- Estender o sistema para permitir nós replicados
Sim

- Difusão baseada em blocos (e não transações individuais)
Sim

- Variantes não-bloqueantes dos comandos
Sim

- Funcionalidade de atrasar, no nó, a execução de cada pedido
Sim

- Suporte ao lançamento de novos nós a qualquer ponto no tempo
Sim

- Tolerância a falhas silenciosas dos nós
Sim

2) Descrevam sucintamente as principais alterações que o grupo aplicou a cada componente indicada abaixo (máx. 800 palavras)
Idealmente, componham uma lista de itens (cada um iniciado por um hífen, tal como a listagem dos requisitos apresentada acima).
Refiram ainda se houve alterações aos argumentos de linha de comando dos executáveis, quais foram e qual a sua justificação.

Aos .proto:

- Mensagens e operações para suportar blocos (Block, DeliverBlockRequest/Response)


Ao programa do Cliente:

- Implementação de variantes não-bloqueantes (comandos c/e/t) recorrendo a stubs assíncronas do gRPC

- Introdução de um identificador único (requestId) por comando, suportando retries e garantindo assim a idempotência

- Implementação de mecanismo de failover: em caso de falha de um nó, o cliente reenvia o pedido para o próximo nó que conhece

- Adição da possibilidade de envio de metadados (delay-seconds) em cada pedido, permitindo um atraso da execução do operação no nó

- Mecanismo de reconexão a nós, recriando o canal gRPC em caso de falha de comunicação


Ao programa do Nó:

- Extensão do nó para suportar execuções de transações baseadas em blocos recebidos do sequenciador

- Thread dedicada (ApplicationPipeline) para obter os blocos do sequenciador e aplicar as operações no nó pela ordem definida

- Operações C/E/T passaram a enviar a transação ao sequenciador e esperar que esta seja aplicada localmente no nó e só depois responder ao cliente

- Possibilidade de atrasos na execução de pedidos através de metadata gRPC, usando um interceptor que extrai o atraso e o aplica antes da execução

- Mecanismo de sincronização inicial de nós (bootStrapSync) para suportar a entrada dinâmica de nós, aplicando localmente e sequencialmente todas as transações de todos os blocos já existentes no sequenciador antes de estar realmente disponível como servidor

- Mecanismo de idempotência que permite detetar pedidos duplicados com base num requestId, garantindo consistência e linearizabilidade 


Ao programa do Sequenciador:

- Estruturas de dados como o bloco aberto em construção e a lista de blocos já fechados, e reformulação do método AddTransaction(), assegurando a extensão do programa para difusão atómica baseada em blocos

- Fecho de blocos com base nos parâmetros N/T: receção dos argumentos e lógica de fecho de blocos quando um bloco atinge N transações ou quando passam T segundos após a entrada da primeira transação no bloco

- Método deliverBlock() que disponibiliza o bloco assim que é fechado através de um mecanismo bloqueante em que o nó espera até que o bloco pedido esteja disponível: waitForBlock()


3) Na vossa solução, as transações recebidas pelo sequenciador levam algum identificador?
Se sim, expliquem brevemente o formato e como é gerado o identificador (máx. 100 palavras)

Sim, cada transação possui um identificador único (requestId), gerado pelo cliente, dado pela concatenação de um identificador aleatório do cliente (UUID) com o número sequencial do comando.

Este identificador é usado pelo nó para garantir idempotência, permitindo detetar pedidos duplicados (devido a retries, por exemplo) e evitar a execução repetida da mesma transação.


4) Como foi frisado na primeira aula teórica, não é aceitável o uso de GenAI/Code Copilots para gerar código usado diretamente no projeto.
Tendo isto em conta, respondam brevemente às seguintes 3 perguntas:

i) Os membros do grupo conseguem explicar o código submetido se tal for perguntado na discussão do projeto (feita sem recurso a AI)?
Sim

ii) Os membros do grupo conseguem defender todas as decisões de desenho e implementação se tal for perguntado na discussão do projeto (feita sem recurso a AI)?
Sim

iii) Usaram alguma(s) ferramenta(s) de AI? Se sim, para que uso?

Foram utilizadas ferramentas de AI apenas como apoio à compreensão do enunciado e de conceitos abordados, bem como para a organização do trabalho e identificação pontual de problemas.

Não foi utilizado código gerado por AI diretamente na implementação do projeto.