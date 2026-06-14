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

output "sqs_queue_url" {
  value       = aws_sqs_queue.dicom_processed_queue.id
  description = "The URL of the SQS queue"
}

output "sqs_queue_arn" {
  value       = aws_sqs_queue.dicom_processed_queue.arn
  description = "The ARN of the SQS queue"
}
