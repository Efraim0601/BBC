package com.bbc.sms.platform.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Stockage objet des fichiers de l'établissement (MinIO, compatible S3).
 *
 * <p>Pourquoi hors de la base, alors que les photos de profil y sont : une photo
 * pèse quelques dizaines de kilo-octets et il y en a une par personne ; un
 * document de la bibliothèque pèse des méga-octets et rien n'en borne le nombre.
 * Les y mettre rendrait le {@code pg_dump} — la sauvegarde de l'établissement —
 * lourd au point de n'être plus fait. La contrepartie est explicite : le volume
 * MinIO doit être sauvegardé à côté de la base, il ne s'y trouve pas.
 *
 * <p>Le bucket est <b>privé</b>. Aucune URL signée n'est distribuée : les octets
 * ne sortent que par un point d'API qui a d'abord vérifié les droits du
 * demandeur. Une clé d'objet devinée ne donne donc rien.
 *
 * <p>Les clés sont préfixées par l'établissement — {@code schools/<id>/<dossier>/…} —
 * de sorte qu'un tenant reste isolé dans le bucket comme il l'est en base.
 */
@Service
public class ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);

    /** Taille de bloc des envois en flux : MinIO impose au moins 5 Mio. */
    private static final long PART_SIZE = 10L * 1024 * 1024;

    private final MinioClient client;
    private final String bucket;

    public ObjectStorage(@Value("${bbc.storage.endpoint}") String endpoint,
                         @Value("${bbc.storage.access-key}") String accessKey,
                         @Value("${bbc.storage.secret-key}") String secretKey,
                         @Value("${bbc.storage.bucket}") String bucket) {
        this.bucket = bucket;
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucket();
    }

    /**
     * Crée le bucket au démarrage s'il manque.
     *
     * <p>Un échec n'interrompt pas le démarrage : le reste de l'application
     * — notes, présences, finances — n'a pas à tomber parce que le stockage des
     * documents est indisponible. L'erreur ressort alors au premier envoi, à
     * l'endroit où elle est compréhensible.
     */
    private void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Stockage objet : bucket « {} » créé.", bucket);
            }
        } catch (Exception e) {
            log.warn("Stockage objet indisponible au démarrage (bucket « {} ») : {}", bucket, e.toString());
        }
    }

    /**
     * Une clé d'objet neuve, dans le dossier d'un établissement.
     *
     * <p>Le nom d'origine ne sert PAS de clé : deux circulaires « note.pdf » se
     * bousculeraient, et un nom hostile (« ../ », accents, espaces) voyagerait
     * jusqu'au stockage. On garde un UUID, et la seule extension du fichier.
     */
    public String newKey(UUID schoolId, String folder, String fileName) {
        return "schools/" + schoolId + "/" + folder + "/" + UUID.randomUUID() + extensionOf(fileName);
    }

    /** Dépose les octets sous cette clé (remplace un objet de même clé). */
    public void put(String key, InputStream data, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(data, size, size > 0 ? -1 : PART_SIZE)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Le fichier n'a pas pu être enregistré", e);
        }
    }

    /**
     * Flux de lecture de l'objet — à fermer par l'appelant.
     * Renvoie null quand l'objet n'existe plus côté stockage.
     */
    public InputStream get(String key) {
        try {
            GetObjectResponse response = client.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(key).build());
            return response;
        } catch (ErrorResponseException e) {
            return null;
        } catch (Exception e) {
            throw new StorageException("Le fichier n'a pas pu être lu", e);
        }
    }

    /**
     * Supprime l'objet. Un objet déjà absent n'est pas une erreur : la ligne en
     * base fait foi, et on la supprime de toute façon.
     */
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            log.warn("Suppression de l'objet « {} » impossible : {}", key, e.toString());
        }
    }

    /** L'extension du nom d'origine, en minuscules et bornée — vide si absente. */
    private static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ext.matches("[a-z0-9]{1,8}")) return "";
        return "." + ext;
    }
}
