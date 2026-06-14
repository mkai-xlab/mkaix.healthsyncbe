
resource "aws_s3_bucket" "dicom_bucket" {
  bucket           = "healthsync-dicom-${var.environment}-819109476069-ap-southeast-1-an"
  bucket_namespace = "account-regional"

  tags = {
    Name        = "healthsync-dicom-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_s3_bucket_public_access_block" "dicom_bucket_public_block" {
  bucket = aws_s3_bucket.dicom_bucket.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket" "png_bucket" {
  bucket           = "healthsync-png-${var.environment}-819109476069-ap-southeast-1-an"
  bucket_namespace = "account-regional"

  tags = {
    Name        = "healthsync-png-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_s3_bucket_public_access_block" "png_bucket_public_block" {
  bucket = aws_s3_bucket.png_bucket.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_sqs_queue" "dicom_processed_queue" {
  name                       = "healthsync-dicom-processed-queue-${var.environment}"
  delay_seconds              = 0
  max_message_size           = 262144 # 256 KB
  message_retention_seconds  = 86400  # 1 day
  receive_wait_time_seconds  = 0
  visibility_timeout_seconds = 90 # Must be greater than Lambda timeout (60 seconds)

  tags = {
    Name        = "healthsync-dicom-processed-queue-${var.environment}"
    Environment = var.environment
  }
}
