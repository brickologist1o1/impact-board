# Impact Board — MVP

Application web de suivi d’impact social construite à partir du cahier des charges et du GANTT fournis. Le code suit la stack prévue : Java 17, Spring Boot, PostgreSQL et interface HTML/CSS/JavaScript responsive.

## Démarrage local

Prérequis : Java 17+, Maven 3.8+ et Docker Desktop.

```powershell
docker compose up -d
mvn spring-boot:run
```

Ouvrir [http://localhost:8080](http://localhost:8080).

Le premier démarrage crée les comptes de démonstration suivants :

| Rôle | Identifiant | Mot de passe |
| --- | --- | --- |
| Project Manager | `manager` | `impact2026` |
| Partenaire (lecture seule) | `partenaire` | `impact2026` |
| Administrateur | `admin` | `impact2026` |

Le projet « École pour toutes » et ses KPI de démonstration sont préchargés. Le partenaire de démonstration y a accès en lecture. Pour utiliser des identifiants différents en production, modifier les comptes de démonstration dans `SeedData.java` avant la première initialisation et définir les variables d’environnement de base de données.

## Fonctions livrées

- Authentification par session et mots de passe BCrypt.
- Création, modification et suppression des projets et des KPI.
- Partage de projet avec des comptes partenaires existants en lecture seule.
- Saisie périodique et historique horodaté des mesures.
- Ajout de plusieurs preuves par mesure, avec validation de la taille (5 Mo), de l’extension, du type MIME et de la signature du fichier (PDF/JPG/JPEG/PNG).
- Tableau de bord avec progression versus objectif et courbe d’évolution.
- Rapport PDF téléchargeable par projet avec objectifs, progression, séries de mesure et références aux preuves.
- Administration d’utilisateurs via l’API protégée par le rôle ADMIN.

## Administration des comptes

La création des comptes est réservée à l’administrateur. Connectez-vous comme `admin`, puis utilisez l’API :

```powershell
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -WebSession $session -ContentType 'application/json' -Body '{"username":"admin","password":"impact2026"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/admin/users -WebSession $session -ContentType 'application/json' -Body '{"username":"nouveau-partenaire","password":"motdepasse-securise","displayName":"Nouveau partenaire","role":"PARTNER"}'
```

Rôles acceptés : `MANAGER`, `PARTNER` et `ADMIN`. Les managers peuvent ensuite saisir le nom d’utilisateur des comptes `PARTNER` dans le champ de partage du projet.

## Configuration

Les valeurs par défaut ciblent PostgreSQL local via `compose.yaml`. Elles peuvent être remplacées avec `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `PORT` et `UPLOAD_DIR`. Les fichiers de preuve sont stockés dans `uploads/`, en dehors du répertoire servi par le site.

`ddl-auto: update` facilite le démarrage du MVP. Pour un déploiement durable, configurer des secrets propres à l’environnement et remplacer la mise à jour automatique du schéma par des migrations versionnées.
