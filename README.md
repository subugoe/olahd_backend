OLA-HD back-end
===============

[OLA-HD](https://ocr-d.de/en/phase3#ola-hd-service--a-generic-service-for-long-term-archiving-of-historical-prints) - A generic Service for the long-term Archiving of Historic Prints

This is the core of the OLA-HD project. OLA-HD is to archive, export and search [OCR-D-ZIP-Files](https://ocr-d.de/en/spec/ocrd_zip). The service uses Spring Boot framework. It contains Elasticsearch and Indexer tool, IIIF Manifest Builder for indexing and searching.

1. [System overview](#1-system-overview)
2. [Installation and startup](#2-installation-and-startup)
3. [OLA-HD API usage](#3-ola-hd-api-usage)
4. [Search and indexing: Elasticsearch, Indexer tool, IIIF Manifest Builder](#4-search-and-indexing-elasticsearch-indexer-tool-iiif-manifest-builder)


1\. System overview
-------------------

![olahd_architecture_overview](olahd_architecture.png "OLA-HD Architecture")

* **Web UI**: [OLA-HD front-end](https://github.com/subugoe/olahd_user_frontend).
* **REST API**: The REST API can be used to import data into the system, export data and search
  through previously saved data. See the [API-usage-section](#3-ola-hd-api-usage) for more
  information.
* **PID Service**: we use
  [GWDG PID Service](https://www.gwdg.de/application-services/persistent-identifier-pid). Every
  uploaded OCRD-ZIP is assigend a unique PID. Each PID is a handle and can be resolved using a
  service from [Handle.Net](https://hdl.handle.net/).
* **Identity Management**: Users have to provide proper credentials to import data to the system.
  Frontend users are authenticated against the
  [GWDG SSO](https://gwdg.de/services/general-services/sso-aai/).
* **Archive Manager (CDSTAR)**: The service that is responsible for storing the data. This service
  is called
  [CDSTAR](https://info.gwdg.de/dokuwiki/doku.php?id=en:services:storage_services:gwdg_cdstar:start)
  and maintained by GWDG. When data is imported, everything will be stored on tapes. To provide
  quick access to users, some data are copied to hard drive. In the current configuration, the
  system does not store TIFF images on hard drive.
* **MongoDB**: the database of the back-end. It stores all import and export information.

The core of OLA-HD is the REST API (the Java-Spring REST Service Backend). It provides an API for
importing, exporting and searching the data. The REST service also offers other functions such as
exporting single files or querying metadata etc. The Backend-Service itself does not save any
archive-data but sends it to the external archive manager CDSTAR. Each saved OCRD-ZIP is assigned a
unique PID via the external PID-Service during import. The OLA-HD-Service uses a MongoDB to store
information about the stored OCRD-ZIPs, such as the CDSTAR internal ID or the PID used. There are
also 4 containers for the search: Web, Redis, Indexer and Elasticsearch. The information about the
data is stored in Elasticsearch so that it can be searched quickly. This data is written to the
Elasticsearch by the Indexer. It extracts this data from the archived files by querying them
through the OLA-HD-Backend. The Web and Redis containers are used to process and forward the
indexing requests from the Backend to the Indexer.


2\. Installation and startup
----------------------------

- tested on Debian/Ubuntu
- ensure `vm.max_map_count` is at least 262144, required for Elasticsearch. Can be tested with
  `sudo sysctl vm.max_map_count`. Can be set (until next boot) with
  `sudo sysctl -w vm.max_map_count=262144` or by adding a configuration file to
  [/etc/sysctl.d](https://man7.org/linux/man-pages/man5/sysctl.d.5.html)
  with content `vm.max_map_count = 262144`
- The application is configured using
  [`src/main/resources/application.properties` and it uses Profiles](https://spring.io/blog/2020/04/23/spring-tips-configuration).
  These files are crucial for the service components to find each other. Depending on running
  locally, running locally in docker or running on a server these files have to be changed.
- OLA-HD Service uses
  [CDSTAR](https://info.gwdg.de/dokuwiki/doku.php?id=en:services:storage_services:gwdg_cdstar:start).
  To run the OLA-HD Service a CDSTAR-vault is needed. This must be correctly set in
  `application.properties` or `application-{profile}.properties`.

### 2.1 Local startup with docker-compose
- requirements:
    - docker
    - docker-compose

`git clone git@github.com:subugoe/olahd_backend`

`cd olahd_backend`

The files `src/main/resources/application-local.properties` and
`src/main/resources/application.properties` are used for the configuration of this deployment. The
CDSTAR-Demo is currently set there for the CDSTAR vault. For this to work, the variable
`cdstar.offlineProfile` is set to `default`, because the CDSTAR demo does not support offline
profiles. To test all the functionalities of OLA-HD you should therefore use your own CDSTAR Vault
which supports offline profiles.

`cp docker.env .env`

`./start.sh`


### 2.2 Local startup for debugging the backend with an IDE
`git clone https://github.com/subugoe/olahd_backend.git`

`cd olahd_backend`

`cp docker.env .env`

`cp localdev/start-localdev.sh .`

Now you can start the backend with an IDE, e.g. Eclipse. For example, Eclipse would now build the
jar and then start the backend, which would roughly correspond to the following two commands:

`./mvnw -q clean package -DskipTests`

`path/to/java/11.0.18-tem/bin/java -jar target/olahd-0.0.1-SNAPSHOT.jar`


3\. OLA-HD API usage
--------------------
<details>
<summary>Import a file</summary>
To import a file, send a `POST` request to the `/bag` endpoint.
This endpoint does not open to public.
Therefore, authentication is needed to access it.

```
curl -X POST \
     http://your.domain.com/api/bag \
     --user <user>:<password> \
     -H 'content-type: multipart/form-data' \
     -F file=@<path-to-file>
```

In the response, a PID is returned in the `Location` header.
</details>

<details>
<summary>Import a new version of a work</summary>
To import a new version, in addition to the `.zip` file, a PID of a previous work version must be submitted as well.

```
curl -X POST \
     http://your.domain.com/api/bag \
     --user <user>:<password> \
     -H 'content-type: multipart/form-data' \
     -F file=@<path-to-file>
     -F prev=<PID-previous-version>
```

</details>

<details>
<summary>Full-text search</summary>
To perform a search, send a `GET` request to the `/search` endpoint.

```
curl -X GET http://your.domain.com/api/search?searchterm=test&fulltextsearch=true&metadatasearch=false
```

</details>

<details>
<summary>Search by meta-data</summary>
Besides full-text search, users can also search by meta-data.

```
curl -X GET http://your.domain.com/api/search?searchterm=test&fulltextsearch=false&metadatasearch=true
```

</details>

<details>
<summary>Quick export</summary>
Data stored on hard drives can be quickly and publicly exported.
To do so, send a `GET` request to the `/export` endpoint.
The `id` must be provided as a URL parameter.

```
curl -X GET http://your.domain.com/api/export?id=your-id --output export.zip
```

</details>

<details>
<summary>Full export request</summary>
To initiate the data movement process from tapes to hard drives, a full export request must be made.
In the request, the identifier of the file is specified.
Then, the archive manager will move this file from tapes to hard drives.
This process takes quite long, hours or days, depending on the real situation.
To send the request, simply send a `GET` request to the `export-request` endpoint with the `id`.

```
curl -X GET http://your.domain.com/api/export-request?id=your-id
```

</details>

<details>
<summary>Full export</summary>
After the export request was successfully fulfilled, the full export can be made.

```
curl -X GET http://your.domain.com/api/full-export?id=your-id --output export.zip
```

</details>


4\. Search and indexing
-----------------------

### Overview
Five services are used for search-indexing in the OLA-HD Service: Web-notifier, Redis, Indexer,
Elasticsearch and S3. For an example of starting all the services together have a look at the script
`start.sh` which is supposed to start the setup locally with docker-compose.

After an OCRD-ZIP-file is imported into CDSTAR, the OLA-HD Service backend sends an indexing request
to the Web-notifier service. The Web-notifier creates an indexing job for this and pushes it to the
`indexer` queue in Redis. The Indexer service is listening on this queue and blocks until a new
message (job) is in the queue. If the indexer gets a new job, it downloads the METS, parses the
structures and creates the index documents. As part of the parsing it also downloads the full-texts
and adds these to the physical index documents to support full-text search. These index documents
are finally saved to Elastiscearch.

After creating the indexing documents the indexer creates a IIIF Manifest and related documents and
stores them in the S3. The IIIF Manifests files can be accessed through the OLA-HD REST API.

### Indexing process
The Indexer loads METS-files, interprets the structures, derives bibliographic and structural
metadata and creates json based index documents. The logical structure and metadata contained in
the METS-file is mapped to the logical index. Each logical structure element in METS corresponds to
a logical index document. The same applies to the physical descriptions in the METS, except that
these are mapped to physical index documents and also contain the full text.

### IIIF Manifests
IIIF Manifests will be created from the index, there is no direct transformation from METS to
manifest. This has several reasons:
* Simplicity: METS is only analyzed in one place to reduce support and maintenance.
* Flexibility of METS: There are different places where to describe e.g. the title, the author and
  one has to check all the different places. Generic tools doesn't do this and possibly lose
  information.
* Control about which metadata goes in the info panel.
* Our viewer (TIFY) currently does not support IIIF Presentation v. 3.0.
* Bad quality of existing mapping tools.
