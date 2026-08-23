# Bibliothèques tierces

## GSAP 3.13.0 — `gsap-3.13.0.min.js`, `ScrollTrigger-3.13.0.min.js`

Moteur d'animation utilisé par `../animations.js` (bannière, apparitions au
défilement, bandeau d'annonces).

Servi **depuis le site** et non depuis un CDN, pour deux raisons :

1. la CSP du site n'autorise que `script-src 'self'` (voir `../../../nginx.conf`) ;
2. une école dont la connexion est instable ne doit pas voir sa page d'accueil
   privée de sa bannière parce qu'un CDN est injoignable.

Le numéro de version est dans le nom du fichier : le contenu ne change jamais
sous ce nom, ce qui autorise un cache navigateur d'un an sans risquer de servir
une version périmée.

Mise à jour :

```bash
V=3.13.0     # remplacer par la version visée
curl -o gsap-$V.min.js          https://unpkg.com/gsap@$V/dist/gsap.min.js
curl -o ScrollTrigger-$V.min.js https://unpkg.com/gsap@$V/dist/ScrollTrigger.min.js
# puis mettre à jour les <script> des six pages HTML et la règle de cache nginx
```

Licence : GSAP « Standard License » (GreenSock), gratuite pour ce type d'usage —
https://gsap.com/standard-license. Conserver l'en-tête de licence en tête des
fichiers minifiés.
