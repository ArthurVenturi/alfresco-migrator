# Alfresco Migrator

<p align='left'>
  <a href="https://github.com/ambientelivre/alfresco-migrator/blob/main/README.md"><strong>Português</strong></a>
    ·
  <a href="https://github.com/ambientelivre/alfresco-migrator/blob/main/README.en_us.md"><strong>Inglês</strong></a>
</p>

### Migrator for Alfresco 2 Alfresco

This plugin enhances the import and export capabilities of Alfresco, enabling the migration of tags, categories, and versions of documents while maintaining their metadata and audit history.

![GIF demostration](/docs/demostracao-migrator.gif)

## Compatibility

This addon has been built to be compatible with Alfresco Community 5.2+ up to the latest release. This project has a branch for each ACS release. For example, the code for ACS 7.4 is in the branch called **`release/7.4`**. The latest release will be on the **`master`** branch.

## Requirements

The requirements correspond to each ACS version of Alfresco. See the [**Supported Platforms and Languages**](https://www.hyland.com/en/resources/alfresco-supported-platforms) page to validate the requirements.

## Building
The releases of this addon are published on the releases page itself. If you want to use the SNAPSHOT build, clone and build it locally and test it using these commands:

1. Clone the Repository

```cmd
  git clone https://github.com/ambientelivre/alfresco-migrator.git
```

2. Build & Test it

```cmd
 ./run.sh build_test
```

3. Test locally
```cmd
 ./run.sh build_start
```

4. Acesse a url do Alfresco Share

```
 http://localhost:8180/share/
```

## Migrator Properties

| Property | Default Value | Description |
| --- | ---: | --- |
| `package-name` | `true` | Name of the exported file created |
| `destination` | `true` | Location where the exported file will be saved |
| `include-children` | `true` | Exports the child content of the selected folder |
| `include-self` | `false` | Exports the selected folder itself |
| `include-versions` | `false` | Exports all document versions |
| `run-in-background` | `true` | Runs operations in the background without blocking user actions. When finished, it will not display a success notification |

## Considerations

* When exporting and importing, keep in mind that importing into sites only works from **`site to site`** and not from sites into folders. This prevents content integrity issues, as folders and sites have different structures.

* When importing, the users must exist in the Alfresco database to import each user's audit history. If not, the **`(user deleted)`** tag will be added after the corresponding username.

## Credits

The UI of this project was embedded with another addon, [**"Import/Export ACP Tool" for Alfresco Share**](https://github.com/atolcd/alfresco-share-import-export). Changes were made to add the functionality of importing multiple files in an `All-in-One` project created from the archetype provided by Alfresco. The objective was to deliver both Repository and Share in a single plugin file.

## Roadmap

* [ ] Refactor and structure code according to Alfresco standards
* [ ] Implement test classes to provide quality status for the exportation and importation process with different documents and elements
* [ ] Enhance the UI feedback
* [ ] Contribute to the Alfresco community repository
