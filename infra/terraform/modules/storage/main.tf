
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


