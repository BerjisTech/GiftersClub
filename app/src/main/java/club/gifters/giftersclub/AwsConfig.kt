package club.gifters.giftersclub

/**
 * Configuration for AWS S3 and CloudFront media upload.
 */
object AwsConfig {
    /**
     * S3 presign URL API endpoint (AWS Lambda + API Gateway).
     * Should point to the getS3PresignedUrl resource.
     * Requests must include a valid Supabase JWT in the Authorization header.
     */
    const val API_URL = "https://u9kqe9rqlj.execute-api.us-east-1.amazonaws.com/prod/getS3PresignedUrl/"

    /** AWS region for S3 buckets. */
    const val REGION = "us-east-1"

    /**
     * S3 bucket names for different media types.
     */
    const val BUCKET_PROFILE = "gifter-club-profile-avatars"
    const val BUCKET_POST = "gifter-club-post-media"
    const val BUCKET_GIFT = "gifter-club-gift-images"

    /** Custom domain for CloudFront distribution. */
    const val CLOUDFRONT_DOMAIN = "cdn.gifters.club"
}