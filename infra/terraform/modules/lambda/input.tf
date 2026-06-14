variable "environment" {
  type        = string
  description = "The deployment environment (e.g. dev, prod)"
}

variable "bucket_name" {
  type        = string
  description = "The name of the DICOM S3 bucket"
}

variable "bucket_arn" {
  type        = string
  description = "The ARN of the DICOM S3 bucket"
}

variable "png_bucket_name" {
  type        = string
  description = "The name of the PNG S3 bucket"
}

variable "png_bucket_arn" {
  type        = string
  description = "The ARN of the PNG S3 bucket"
}

variable "sqs_queue_url" {
  type        = string
  description = "The URL of the SQS queue"
}

variable "sqs_queue_arn" {
  type        = string
  description = "The ARN of the SQS queue"
}
