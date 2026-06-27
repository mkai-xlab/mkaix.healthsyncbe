output "ec2_public_ip" {
  description = "The public IP of the EC2 instance"
  value       = module.compute.server_public_ip
}


