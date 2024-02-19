This folder and files are used to set up the local eclipse development environment. This starts
all containers the backend needs with docker, except the backend itself. This is started in eclipse
(STS) to be able to use debug mode etc.

The files are collected in this folder. To prepare the startup the files are copied to where they
are needed. This destinations are ignored with .gitignore to not interfere with the "normal" usage.

- Prepare:
```
cp mystart-eclipse.sh docker-compose-eclipse.yaml ..
cp -r cfg/eclipse ../cfg/eclipse
```

- The setup is started from the base folder of the project. This starts all containers the backend
  needs
```
cd ..
./mystart-eclipse.sh
```
