variable "vpc_cidr" {
  type = string
}

variable "environment" {
  type        = string
  description = "The environment name (e.g. dev, prod)"
}
