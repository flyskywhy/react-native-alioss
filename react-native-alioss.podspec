require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name         = 'react-native-alioss'
  s.version      = package['version']
  s.summary      = package['description']
  s.license      = package['license']

  s.authors      = package['author']
  s.homepage     = package['homepage']
  s.platform     = :ios, "9.0"

  s.source       = { :git => "https://github.com/flyskywhy/react-native-alioss.git", :tag => "v#{s.version}" }
  s.source_files  = "ios/**/*.{h,m}"
  s.static_framework   = true

  s.dependency 'React'
  s.dependency "AliyunOSSiOS", "2.10.14"
end
