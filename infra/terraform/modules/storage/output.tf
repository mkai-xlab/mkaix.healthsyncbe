output "bucket_name" {
  value       = aws_s3_bucket.dicom_bucket.id
  description = "The name of the DICOM S3 bucket"
}

output "bucket_arn" {
  value       = aws_s3_bucket.dicom_bucket.arn
  description = "The ARN of the DICOM S3 bucket"
}

output "png_bucket_name" {
  value       = aws_s3_bucket.png_bucket.id
  description = "The name of the PNG S3 bucket"
}

output "png_bucket_arn" {
  value       = aws_s3_bucket.png_bucket.arn
  description = "The ARN of the PNG S3 bucket"
}


