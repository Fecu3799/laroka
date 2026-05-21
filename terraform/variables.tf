variable "render_api_key" {
  type      = string
  sensitive = true
}
variable "render_owner_id" {
  type      = string
  sensitive = true
}
variable "db_url" { 
    type = string 
}
variable "db_user" { 
    type = string 
}
variable "db_pass" { 
    type = string
    sensitive = true 
}
variable "jwt_secret" { 
    type = string
    sensitive = true 
}
variable "jwt_expiration" { 
    type = string 
}
variable "new_relic_license_key" { 
    type = string
    sensitive = true 
}
variable "mercadopago_access_token" {
  type      = string
  sensitive = true
}
variable "mercadopago_webhook_secret" {
  type      = string
  sensitive = true
}
variable "r2_access_key" {
  type      = string
  sensitive = true
}
variable "r2_secret_key" {
  type      = string
  sensitive = true
}
variable "r2_bucket_name" {
  type = string
}
variable "r2_endpoint" {
  type = string
}
variable "cors_allowed_origins" {
  type = string
}