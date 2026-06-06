terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
  backend "s3" {
    bucket = "terraform-state-819109476069-ap-southeast-1-an"
    key    = "dev/terraform.tfstate"
    region = "ap-southeast-1"
  }
}

# Configure the AWS Provider
provider "aws" {
  region = "ap-southeast-1"
  default_tags {
    tags = {
      "env" : "dev"
      "owner" : "viet"
      "email" : "viet.ngdang.dev@gmail.com"
      "project" : "server"
    }
  }
}
