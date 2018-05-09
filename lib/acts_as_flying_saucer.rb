require 'acts_as_flying_saucer/config'
require 'acts_as_flying_saucer/xhtml2pdf'
require 'acts_as_flying_saucer/acts_as_flying_saucer_controller'
require 'nailgun'
require 'tidy_ffi'
require 'net/http'
require 'uri'
if defined?(Rails)
  ActionController::Base.send(:include, ActsAsFlyingSaucer::Controller)
elsif defined?(Sinatra)
  Sinatra::Base.send(:include, ActsAsFlyingSaucer::Controller)
  class Sinatra::Base
    acts_as_flying_saucer
  end
end

module ActsAsFlyingSaucer
  class HtmlToPdfConverter
    def self.convert html, options = {}
      html = TidyFFI::Tidy.new(html,:output_xhtml=>true,:numeric_entities=>true).clean if options[:tidy_clean]
      tmp_dir = ActsAsFlyingSaucer::Config.options[:tmp_path]
      html_digest = Digest::MD5.hexdigest(html)
      input_file =File.join(File.expand_path("#{tmp_dir}"),"#{html_digest}.html")

      output_file = (options.has_key?(:pdf_file)) ? options[:pdf_file] : File.join(File.expand_path("#{tmp_dir}"),"#{html_digest}.pdf")
      password = (options.has_key?(:password)) ? options[:password] : ""
      cache = options.has_key?(:cache) ? options[:cache] : (Rails.env.development? ? 'false' : 'true')
      generate_options = ActsAsFlyingSaucer::Config.options.merge({
                                                                          :input_file => input_file,
                                                                          :output_file => output_file,
                                                                          :html => html,
                                                                          :cache => cache
                                                                  })

      ActsAsFlyingSaucer::Xhtml2Pdf.write_pdf(generate_options)

      if  password != ""
        op=output_file.split(".")
        op.pop
        op  << "a"
        op=op.to_s+".pdf"
        output_file_name =  op
        ActsAsFlyingSaucer::Xhtml2Pdf.encrypt_pdf(generate_options,output_file_name,password)
        output_file = op
      end
      output_file
    end
  end
end