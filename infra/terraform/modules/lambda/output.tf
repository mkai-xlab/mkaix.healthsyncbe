output "lambda_arn" {
  value       = aws_lambda_function.dicom_processor.arn
  description = "The ARN of the DICOM processor Lambda function"
}

output "lambda_name" {
  value       = aws_lambda_function.dicom_processor.function_name
  description = "The name of the DICOM processor Lambda function"
}
