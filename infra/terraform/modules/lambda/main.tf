# IAM Role for Lambda
resource "aws_iam_role" "lambda_role" {
  name = "healthsync-dicom-processor-role-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "healthsync-dicom-processor-role-${var.environment}"
    Environment = var.environment
  }
}

# Attach Basic Execution Role Policy for CloudWatch Logs
resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Inline policy for S3 and SQS access
resource "aws_iam_role_policy" "lambda_policy" {
  name = "healthsync-dicom-processor-policy-${var.environment}"
  role = aws_iam_role.lambda_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = [
          "${var.bucket_arn}/*",
          "${var.png_bucket_arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "sqs:SendMessage"
        ]
        Resource = var.sqs_queue_arn
      }
    ]
  })
}

# Path to the compiled Lambda jar
locals {
  jar_path = "${path.module}/../../../lambda/dicom-processor/target/dicom-processor-1.0-SNAPSHOT.jar"
}

# Lambda Function
resource "aws_lambda_function" "dicom_processor" {
  function_name    = "healthsync-dicom-processor-${var.environment}"
  runtime          = "java21"
  handler          = "com.healthsync.lambda.DicomProcessorHandler::handleRequest"
  filename         = local.jar_path
  source_code_hash = fileexists(local.jar_path) ? filebase64sha256(local.jar_path) : null
  role             = aws_iam_role.lambda_role.arn
  memory_size      = 512
  timeout          = 60

  environment {
    variables = {
      DICOM_BUCKET  = var.bucket_name
      PNG_BUCKET    = var.png_bucket_name
      SQS_QUEUE_URL = var.sqs_queue_url
    }
  }

  tags = {
    Name        = "healthsync-dicom-processor-${var.environment}"
    Environment = var.environment
  }
}

# Allow S3 bucket to invoke Lambda
resource "aws_lambda_permission" "allow_s3" {
  statement_id  = "AllowExecutionFromS3Bucket"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.dicom_processor.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = var.bucket_arn
}

# S3 Bucket Notification Configuration
resource "aws_s3_bucket_notification" "bucket_notification" {
  bucket = var.bucket_name

  lambda_function {
    lambda_function_arn = aws_lambda_function.dicom_processor.arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "dicom/"
    filter_suffix       = ".dcm"
  }

  depends_on = [aws_lambda_permission.allow_s3]
}
