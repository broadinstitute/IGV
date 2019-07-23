package org.broad.igv.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import htsjdk.samtools.util.Tuple;
import org.apache.log4j.Logger;
import org.broad.igv.DirectoryManager;
import org.broad.igv.aws.IGVS3Object;
import org.broad.igv.google.OAuthUtils;
import org.broad.igv.ui.util.MessageUtils;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.io.SdkFilterInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient;
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClientBuilder;
import software.amazon.awssdk.services.cognitoidentity.model.*;
import software.amazon.awssdk.services.s3.S3BaseClientBuilder;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

public class AmazonUtils {
    private static Logger log = Logger.getLogger(AmazonUtils.class);

    // AWS specific objects
    // XXX: Centralized
    // private static CredsProvider;
    private static S3Client s3Client;
    private static CognitoIdentityClient cognitoIdentityClient;
    private static Region AWSREGION = Region.of(GetCognitoConfig().get("aws_region").getAsString());

    public static JsonObject GetCognitoConfig() {
        try {
            // Get AWS-specific Cognito details from on-disk JSON preferences
            String oauthConfig = DirectoryManager.getIgvDirectory() + "/oauth-config.json";
            JsonParser parser = new JsonParser();
            String json_file = FileUtils.getContents(oauthConfig);
            JsonObject json_obj = parser.parse(json_file).getAsJsonObject();
            return json_obj;
        } catch (IOException io) {
            log.error("Cannot read oauth-config.json config file");
        }

        return null;
    }

    /**
     * Returns the AWS credentials
     *
     * @return returns the credentials based on the AWS STS access token returned from the AWS Cognito user pool.
     */
    public static Credentials GetCognitoAWSCredentials() {

        JsonObject igv_oauth_conf = GetCognitoConfig();
        JsonObject response = OAuthUtils.getResponse();

        JsonObject payload = JWTParser.getPayload(response.get("id_token").getAsString());

        log.debug("JWT payload id token: "+payload);

        String idTokenStr = response.get("id_token").getAsString();
        String idProvider = payload.get("iss").toString().replace("https://", "")
                                                         .replace("\"", "");
        HashMap<String, String> logins = new HashMap<>();
        logins.put(idProvider, idTokenStr);

        String federatedPoolId = igv_oauth_conf.get("aws_cognito_fed_pool_id").getAsString();

        // https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/cognitoidentity/CognitoIdentityClient.html
        // Build the Cognito client
        CognitoIdentityClientBuilder cognitoIdentityBuilder = CognitoIdentityClient.builder();

        // Avoid "software.amazon.awssdk.core.exception.SdkClientException: Unable to load credentials from any of the providers in the chain AwsCredentialsProviderChain("
        // https://stackoverflow.com/questions/36604024/sts-saml-and-java-sdk-unable-to-load-aws-credentials-from-any-provider-in-the-c
        cognitoIdentityBuilder.region(AWSREGION).credentialsProvider(AnonymousCredentialsProvider.create());
        cognitoIdentityClient = cognitoIdentityBuilder.build();


        // "To provide end-user credentials, first make an unsigned call to GetId."
        GetIdRequest.Builder idrequest = GetIdRequest.builder().identityPoolId(federatedPoolId)
                                                               .logins(logins);
        GetIdResponse idResult = cognitoIdentityClient.getId(idrequest.build());

        // "Next, make an unsigned call to GetCredentialsForIdentity."
        GetCredentialsForIdentityRequest.Builder authedIds = GetCredentialsForIdentityRequest.builder();
        authedIds.identityId(idResult.identityId()).logins(logins);

        GetCredentialsForIdentityResponse authedRes = cognitoIdentityClient.getCredentialsForIdentity(authedIds.build());

        return authedRes.credentials();
    }


    /**
     * Makes sure the S3 client is available for bucket operations and/or generation of pre-signed urls
     *
     * @param credentials AWS credentials
     *
     */
    public static void updateS3Client(Credentials credentials) {
        AwsSessionCredentials creds = AwsSessionCredentials.create(credentials.accessKeyId(),
                                                                   credentials.secretKey(),
                                                                   credentials.sessionToken());

        StaticCredentialsProvider s3CredsProvider = StaticCredentialsProvider.create(creds);
        s3Client = S3Client.builder().credentialsProvider(s3CredsProvider).region(AWSREGION).build();
    }


    /**
     * This method returns the details of the user and bucket lists.
     * @return bucket list
     */
    public static ArrayList<String> ListBucketsForUser() {
        ArrayList<String> bucketsList = new ArrayList<>();

        try {
            OAuthUtils.getInstance().getAccessToken();
            updateS3Client(GetCognitoAWSCredentials());
        } catch (IOException e) {
            e.printStackTrace();
        }

        ListBucketsRequest listBucketsRequest = ListBucketsRequest.builder().build();
        ListBucketsResponse listBucketsResponse = s3Client.listBuckets(listBucketsRequest);
        // XXX: Filter out buckets that I do not have permissions for
        listBucketsResponse.buckets().stream().forEach(x -> bucketsList.add(x.name()));

        return bucketsList;
    }

    /**
     * Returns a list of bucket objects given a specific path.
     *
     * Adopted from:
     * https://docs.aws.amazon.com/AmazonS3/latest/dev/ListingObjectKeysUsingJava.html
     *
     * @return List of bucket objects
     */

    public static ArrayList<IGVS3Object> ListBucketObjects(String bucketName, String prefix) {
        ArrayList<IGVS3Object> objects = new ArrayList<>();
        log.debug("Listing objects for bucketName: "+ bucketName);

        try {
            OAuthUtils.getInstance().getAccessToken();
            updateS3Client(GetCognitoAWSCredentials());
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            // https://docs.aws.amazon.com/AmazonS3/latest/dev/UsingMetadata.html
            // """
            // The Amazon S3 data model is a flat structure: you create a bucket, and the bucket stores
            // objects. There is no hierarchy of subbuckets or subfolders; however, you can infer logical
            // hierarchy using key name prefixes and delimiters as the Amazon S3 console does. The Amazon
            // S3 console supports a concept of folders.
            // """
            ListObjectsV2Request listReq = ListObjectsV2Request.builder().bucket(bucketName)
                                                                         .prefix(prefix)
                                                                         .delimiter("/")
                                                                         .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(listReq);
            ListObjectsV2Iterable resultIt = s3Client.listObjectsV2Paginator(listReq);
            String folder_prefix;

            do {
                for (CommonPrefix folder : resultIt.commonPrefixes()) {
                    log.debug("S3 Bucket folder: "+folder);
                    folder_prefix = folder.prefix().substring(0, folder.prefix().length()-1); // Chop off last / of the folder for UI purposes
                    objects.add(new IGVS3Object(folder_prefix.replace(prefix, ""), true));
                }

                for (S3Object content: response.contents()) {
                    log.debug("S3 Bucket key: "+content.key());
                    objects.add(new IGVS3Object(content.key().replace(prefix, ""), false));
                }
                // If there are more than maxKeys keys in the bucket, get a continuation token
                // and list the next objects.
                String token = response.nextContinuationToken();
                log.debug("Next S3 bucket pagination continuation Token: " + token);
                response.continuationToken();
            } while (response.isTruncated());

        } catch(SdkServiceException e) {
            // The call was transmitted successfully, but Amazon S3 couldn't process
            // it, so it returned an error response.
            e.printStackTrace();
        } catch(SdkClientException e) {
            // Amazon S3 couldn't be contacted for a response, or the client
            // couldn't parse the response from Amazon S3.
            e.printStackTrace();
        }

        return objects;
    }

    public static Tuple<String, String> bucketAndKey(String S3urlString) {
        AmazonS3URI s3URI = new AmazonS3URI(S3urlString);
        String bucket = s3URI.getBucket();
        String key = s3URI.getKey();

        log.debug("bucketAndKey(): "+ bucket + " , " + key);
        return new Tuple(bucket, key);
    }

    // Amazon S3 Presign URLs
    public static String translateAmazonCloudURL(String s3UrlString) throws IOException {
        // Make sure access token are valid (refreshes token internally)
        OAuthUtils.getInstance().getAccessToken();

        Credentials credentials = GetCognitoAWSCredentials();
        AwsSessionCredentials creds = AwsSessionCredentials.create(credentials.accessKeyId(),
                                                                   credentials.secretKey(),
                                                                   credentials.sessionToken());
        StaticCredentialsProvider awsCredsProvider = StaticCredentialsProvider.create(creds);

        S3Presigner s3Presigner = S3Presigner.builder()
                                             .expiration(OAuthUtils.getExpirationTime())
                                             .awsCredentials(awsCredsProvider)
                                             .region(AWSREGION)
                                             .build();

        Tuple<String, String> bandk = bucketAndKey(s3UrlString);
        String bucket = bandk.a;
        String filename = bandk.b;

        URI presigned = s3Presigner.presignS3DownloadLink(bucket, filename);
        log.debug("AWS presigned URL from translateAmazonCloudURL is: "+presigned);
        return presigned.toString();
    }

    public static Boolean isAwsS3Path(String path) {
        // TODO: perhaps add some more checks
        return (path.startsWith("s3://"));
    }

    public static void checkLogin() {
        try {
            if (!OAuthUtils.getInstance().isLoggedIn()) {
                OAuthUtils.getInstance().doSecureLogin();
            }
        } catch (IOException e) {
            MessageUtils.showErrorMessage("Error initializing OAuth", e);
            log.error("Error initializing OAuth", e);
        }
    }
}