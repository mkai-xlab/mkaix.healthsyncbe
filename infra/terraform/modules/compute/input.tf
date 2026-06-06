variable "ami_id" {
  type = string
}

variable "instance_type" {
  type = string
}

variable "subnet_ids" {
  type = list(string)
}

variable "key_path" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "vpc_cidr" {
  type = string
}

variable "environment" {
  type        = string
  description = "The environment name (e.g. dev, prod)"
}

variable "user_data" {
  type        = string
  description = "The user data script to run on instance start"
  default     = null
}

