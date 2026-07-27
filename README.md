# Alfresco Migrator

<p align='left'>
  <a href="https://github.com/ambientelivre/alfresco-migrator/blob/main/README.md"><strong>Portuguese</strong></a>
    ·
  <a href="https://github.com/ambientelivre/alfresco-migrator/blob/main/README.en_us.md"><strong>English</strong></a>
</p>

### Migrador de Alfresco para Alfresco

Este plugin aprimora as capacidades de importação e exportação do Alfresco, permitindo a migração de tags, categorias e versões de documentos, mantendo seus metadados e histórico de auditoria.

![GIF demonstracao](/docs/demostracao-migrator.gif)

## Compatibilidade

Este addon foi construído para ser compatível com o Alfresco Community 5.2+ até a versão mais recente. Este projeto possui uma branch para cada versão do ACS. Por exemplo, o código para o ACS 7.4 está na branch chamada **`release/7.4`**. A versão mais recente estará na branch **`master`**.

## Requisitos 

Os requisitos correspondem a cada versão do ACS do Alfresco. Consulte a página de [**Plataformas e Idiomas Suportados**](https://www.hyland.com/en/resources/alfresco-supported-platforms) para validar os requisitos. 

## Compilação
As versões (releases) deste addon são publicadas na própria página de releases do repositório. Se você deseja usar a versão SNAPSHOT, clone e compile o projeto localmente, e teste usando os seguintes comandos: 

1. Clonar o Repositório

```cmd
  git clone https://github.com/ambientelivre/alfresco-migrator.git
```

2. Compilar e Testar

```cmd
 ./run.sh build_test
```

3. Testar localmente 
```cmd
 ./run.sh build_start
```

4. Acesse a url do Alfresco Share

```
 http://localhost:8180/share/
```

## Considerações

* Ao exportar e importar, tenha em mente que a importação em sites só funciona de **`site para site`**, e não de sites para pastas. Isso evita problemas de integridade do conteúdo, visto que pastas e sites possuem estruturas diferentes.

* Ao realizar a importação, os usuários devem existir na base de dados do Alfresco para que o histórico de auditoria de cada um seja importado. Caso contrário, a tag **`(user deleted)`** será adicionada após o nome de usuário correspondente.

## Propriedades do Migrador

| Propriedade | Valor Padrão | Descrição |
| --- | ---: | --- |
| `package-name` | `true` | Nome do arquivo exportado criado |
| `destination` | `true` | Local onde o arquivo exportado será salvo |
| `include-children` | `true` | Exporta o conteúdo filho da pasta selecionada |
| `include-self` | `false` | Exporta a própria pasta selecionada |
| `include-versions` | `false` | Exporta as versões de todos os documentos |
| `run-in-background` | `true` | Executa as operações em segundo plano sem bloquear as ações do usuário. Ao finalizar, não exibirá notificação de sucesso |

## Créditos

A interface de usuário (UI) deste projeto foi incorporada a partir de outro addon, o [**"Import/Export ACP Tool" for Alfresco Share**](https://github.com/atolcd/alfresco-share-import-export). Foram feitas alterações para adicionar a funcionalidade de importar múltiplos arquivos em um projeto `All-in-One`, criado a partir do arquétipo fornecido pelo Alfresco. O objetivo é entregar o Repository e o Share em um único arquivo de plugin.

## Roadmap 

* [ ] Refatorar e estruturar o código de acordo com os padrões do Alfresco
* [ ] Implementar classes de teste para garantir a qualidade do processo de exportação e importação com diferentes documentos e elementos
* [ ] Melhorar o feedback da interface de usuário (UI)
* [ ] Contribuir com o repositório da comunidade Alfresco